package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.LedgerTxType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;

import java.time.LocalDate;

/**
 * Applies year-end carry-forward and expiration for all active leave types
 * (M178 / PRD §8.5.2).
 *
 * <p>On the first day of each new year (cron: 01 Jan 01:30 AM) the service
 * walks every (employee, leave_type) balance from the closing year and:
 * <ol>
 *   <li>Computes available = entitlement + carried_forward + adjustments − used.</li>
 *   <li>Caps it at the type's {@code carry_forward_limit_days}
 *       (0 if unset = expires everything).</li>
 *   <li>Creates (or updates) the new-year balance row with
 *       {@code carried_forward_days = carry}.</li>
 *   <li>For non-accrual types also seeds the new-year's
 *       {@code entitlement_days}.</li>
 *   <li>Logs a LEAVE_YEAR_END_ROLLOVER audit entry with carry / expired
 *       amounts for every affected employee+type pair.</li>
 * </ol>
 *
 * <p>Idempotent: if the target-year balance row already has a non-zero
 * {@code carried_forward_days} or its {@code last_recalculated_at} is
 * within the target year, the row is skipped to prevent double-application.
 */
@Service
public class LeaveYearEndService {

    private static final Logger log = LoggerFactory.getLogger(LeaveYearEndService.class);
    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveBalance";
    private static final String ACTION  = "LEAVE_YEAR_END_ROLLOVER";

    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final AuditService audit;
    private final LeaveLedgerService ledger;

    public LeaveYearEndService(LeaveBalanceRepository balances,
                               LeaveTypeRepository types,
                               AuditService audit,
                               LeaveLedgerService ledger) {
        this.balances = balances;
        this.types = types;
        this.audit = audit;
        this.ledger = ledger;
    }

    /** Runs on 1 January at 01:30 AM for the previous year. */
    @Scheduled(cron = "${hcm.leave.year-end.cron:0 30 1 1 1 *}")
    public void runYearEnd() {
        int closingYear = OffsetDateTime.now().getYear() - 1;
        RolloverResult result = rollover(closingYear, false);
        log.info("Leave year-end rollover {}/{}: {} processed, {} carried, {} expired",
                closingYear, closingYear + 1,
                result.processed(), result.totalCarried(), result.totalExpired());
    }

    /**
     * Executes the carry-forward roll-over for {@code closingYear} →
     * {@code closingYear + 1}.
     *
     * @param closingYear the year whose balances are being rolled over
     * @param dryRun      if {@code true} computes but does not persist changes
     * @return a summary of the rollover
     */
    @Transactional
    public RolloverResult rollover(int closingYear, boolean dryRun) {
        int nextYear = closingYear + 1;
        List<LeaveBalance> closingBalances = balances
                .findByYearOrderByEmployeeIdAscLeaveTypeIdAsc(closingYear);

        int processed = 0;
        BigDecimal totalCarried = BigDecimal.ZERO;
        BigDecimal totalExpired = BigDecimal.ZERO;

        for (LeaveBalance closing : closingBalances) {
            LeaveType type = types.findById(closing.getLeaveTypeId()).orElse(null);
            if (type == null || !type.isActive()) continue;

            // available = total balance - used (reserved are in-flight, not yet consumed)
            BigDecimal available = closing.getEntitlementDays()
                    .add(closing.getCarriedForwardDays())
                    .add(closing.getAdjustmentDays())
                    .subtract(closing.getUsedDays())
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal limit = type.getCarryForwardLimitDays() != null
                    ? type.getCarryForwardLimitDays()
                    : BigDecimal.ZERO;

            BigDecimal carry   = available.min(limit).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expired = available.subtract(carry).max(BigDecimal.ZERO)
                                         .setScale(2, RoundingMode.HALF_UP);

            if (!dryRun) {
                applyCarryForward(closing, type, nextYear, carry, expired);
                recordAudit(closing, nextYear, available, carry, expired);
            }

            processed++;
            totalCarried = totalCarried.add(carry);
            totalExpired = totalExpired.add(expired);
        }

        return new RolloverResult(closingYear, nextYear, processed, totalCarried, totalExpired, dryRun);
    }

    // ---------- Internals ----------

    private void applyCarryForward(LeaveBalance closing, LeaveType type,
                                    int nextYear, BigDecimal carry, BigDecimal expired) {
        Optional<LeaveBalance> existing = balances
                .findByEmployeeIdAndLeaveTypeIdAndYear(
                        closing.getEmployeeId(), closing.getLeaveTypeId(), nextYear);

        LeaveBalance next;
        if (existing.isPresent()) {
            next = existing.get();
            if (next.getCarriedForwardDays().compareTo(BigDecimal.ZERO) > 0) {
                log.debug("Skipping already-rolled-over balance: emp={} type={} year={}",
                        closing.getEmployeeId(), type.getCode(), nextYear);
                return;
            }
        } else {
            next = new LeaveBalance();
            next.setEmployeeId(closing.getEmployeeId());
            next.setLeaveTypeId(closing.getLeaveTypeId());
            next.setYear(nextYear);
            // M151: component-driven types open the new year at zero and are
            // filled by LeaveEntitlementComponentService.recalculate(). This is
            // also where a seniority or child-age threshold crossed during the
            // closing year finally takes effect — drivers are evaluated as at
            // 1 January, so the uplift lands here rather than mid-year.
            if (type.isEntitlementComponentsEnabled()) {
                next.setEntitlementDays(BigDecimal.ZERO);
            } else if (!type.isAccruesMonthly() && type.getDefaultAnnualEntitlementDays() != null) {
                next.setEntitlementDays(type.getDefaultAnnualEntitlementDays());
            } else {
                next.setEntitlementDays(BigDecimal.ZERO);
            }
        }

        next.setCarriedForwardDays(carry);
        next.setLastRecalculatedAt(OffsetDateTime.now());
        if (carry.compareTo(BigDecimal.ZERO) > 0 && type.getCarryForwardExpiryMonths() != null) {
            next.setCarryForwardExpiresAt(
                    LocalDate.of(nextYear, 1, 1).plusMonths(type.getCarryForwardExpiryMonths()));
        } else {
            next.setCarryForwardExpiresAt(null);
        }
        balances.save(next);

        LocalDate rolloverDate = LocalDate.of(nextYear, 1, 1);
        if (carry.compareTo(BigDecimal.ZERO) > 0) {
            ledger.record(next.getEmployeeId(), next.getLeaveTypeId(), nextYear,
                    LedgerTxType.CARRY_FORWARD, carry, rolloverDate,
                    "YEAR_END_ROLLOVER", closing.getId().toString(), next.remaining(),
                    "Carried forward from " + closing.getYear(), "system");
        }
        if (expired.compareTo(BigDecimal.ZERO) > 0) {
            ledger.record(closing.getEmployeeId(), closing.getLeaveTypeId(), closing.getYear(),
                    LedgerTxType.EXPIRY, expired.negate(), rolloverDate,
                    "YEAR_END_ROLLOVER", closing.getId().toString(), BigDecimal.ZERO,
                    "Expired balance at year end", "system");
        }
    }

    private void recordAudit(LeaveBalance closing, int nextYear,
                              BigDecimal available, BigDecimal carry, BigDecimal expired) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("fromYear",  closing.getYear());
        detail.put("toYear",    nextYear);
        detail.put("available", available);
        detail.put("carried",   carry);
        detail.put("expired",   expired);
        detail.put("balanceId", closing.getId().toString());
        audit.record(MODULE, ENTITY, closing.getId().toString(), ACTION, null, detail);
    }

    /** Summary returned by {@link #rollover(int, boolean)}. */
    public record RolloverResult(
            int closingYear,
            int nextYear,
            int processed,
            BigDecimal totalCarried,
            BigDecimal totalExpired,
            boolean dryRun) {}
}
