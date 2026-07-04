package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LedgerTxType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;

/**
 * M340: Daily sweep that expires mid-year carry-forward days when
 * leave_balance.carry_forward_expires_at <= today.
 *
 * <p>The expiry date is stamped at year-end roll-over time by
 * {@link LeaveYearEndService} when the leave type has
 * {@code carryForwardExpiryMonths} configured. This scheduler is the
 * actual enforcement arm — it debits the remaining carried-forward days
 * via an EXPIRY ledger entry and zeroes the field so the balance is
 * accurate from the next request onward.
 *
 * <p>Idempotent: a balance with zero carried_forward_days is skipped.
 */
@Service
public class CarryForwardExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CarryForwardExpiryScheduler.class);
    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveBalance";

    private final LeaveBalanceRepository balances;
    private final LeaveLedgerService ledger;
    private final AuditService audit;

    public CarryForwardExpiryScheduler(LeaveBalanceRepository balances,
                                       LeaveLedgerService ledger,
                                       AuditService audit) {
        this.balances = balances;
        this.ledger = ledger;
        this.audit = audit;
    }

    /** Runs daily at 02:15 AM (configurable via hcm.leave.cf-expiry.cron). */
    @Scheduled(cron = "${hcm.leave.cf-expiry.cron:0 15 2 * * *}")
    public void expireCarryForward() {
        int expired = sweep(LocalDate.now());
        if (expired > 0) {
            log.info("Carry-forward expiry sweep: {} balance(s) expired", expired);
        }
    }

    /**
     * Expires all carry-forward balances whose expiry date is on or before {@code asOf}.
     *
     * @return count of balances expired
     */
    @Transactional
    public int sweep(LocalDate asOf) {
        List<LeaveBalance> due = balances
                .findByCarryForwardExpiresAtBeforeAndCarriedForwardDaysGreaterThan(
                        asOf.plusDays(1), BigDecimal.ZERO);

        for (LeaveBalance b : due) {
            BigDecimal toExpire = b.getCarriedForwardDays();
            b.setCarriedForwardDays(BigDecimal.ZERO);
            b.setCarryForwardExpiresAt(null);
            balances.save(b);

            ledger.record(b.getEmployeeId(), b.getLeaveTypeId(), b.getYear(),
                    LedgerTxType.EXPIRY, toExpire.negate(), asOf,
                    "CF_EXPIRY", b.getId().toString(), b.remaining(),
                    "Carry-forward days expired on " + asOf, "system");

            audit.record(MODULE, ENTITY, b.getId().toString(),
                    "CARRY_FORWARD_EXPIRED", null,
                    java.util.Map.of(
                            "expiredDays", toExpire,
                            "expiredOn", asOf.toString(),
                            "year", b.getYear()));
        }
        return due.size();
    }
}
