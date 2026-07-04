package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.leave.api.dto.LeaveBalanceAdjustment;
import az.millers.hcm.leave.api.dto.LeaveBalanceResponse;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.LedgerTxType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Manages per-employee, per-type, per-year leave balances (PRD 8.5.2).
 *
 * <p>Balances are lazily initialised on first touch: when a request is
 * submitted or the balance is queried, a row is created with entitlement
 * = leave_type.default_annual_entitlement_days. Monthly accrual,
 * pro-ration on hire, and seniority bumps are explicit later-work seams.
 */
@Service
public class LeaveBalanceService {

    private static final String MODULE = "LEAVE";

    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final AuditService audit;
    private final LeaveLedgerService ledger;
    private final CurrentRequest currentRequest;

    public LeaveBalanceService(LeaveBalanceRepository balances,
                               LeaveTypeRepository types,
                               AuditService audit,
                               LeaveLedgerService ledger,
                               CurrentRequest currentRequest) {
        this.balances = balances;
        this.types = types;
        this.audit = audit;
        this.ledger = ledger;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalance> listForEmployee(UUID employeeId, int year) {
        return balances.findByEmployeeIdAndYearOrderByLeaveTypeId(employeeId, year);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalance> listForYear(int year) {
        return balances.findByYearOrderByEmployeeIdAscLeaveTypeIdAsc(year);
    }

    @Transactional
    public LeaveBalance ensureBalance(UUID employeeId, UUID leaveTypeId, int year) {
        return balances.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .orElseGet(() -> initialiseBalance(employeeId, leaveTypeId, year));
    }

    @Transactional
    public LeaveBalance applyAdjustment(LeaveBalanceAdjustment req) {
        LeaveBalance b = ensureBalance(req.employeeId(), req.leaveTypeId(), req.year());
        LeaveBalanceResponse before = LeaveBalanceResponse.from(b);
        b.setAdjustmentDays(b.getAdjustmentDays().add(req.deltaDays()));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        LeaveBalance saved = balances.save(b);
        audit.record(MODULE, "LeaveBalance", saved.getId().toString(),
                "ADJUST", before, LeaveBalanceResponse.from(saved));
        ledger.record(saved.getEmployeeId(), saved.getLeaveTypeId(), saved.getYear(),
                LedgerTxType.ADJUSTMENT, req.deltaDays(), LocalDate.now(),
                "ADJUSTMENT", saved.getId().toString(), saved.remaining(),
                req.reason(), currentRequest.username());
        return saved;
    }

    /** Called by {@code LeaveRequestService} when a request is submitted. */
    @Transactional
    public LeaveBalance reserve(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days,
                                 LeaveType type) {
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year);
        boolean hasEntitlement = type.getDefaultAnnualEntitlementDays() != null
                && type.getDefaultAnnualEntitlementDays().signum() > 0;
        if (hasEntitlement) {
            BigDecimal remaining = b.remaining();
            if (type.isNegativeBalanceAllowed()) {
                BigDecimal floor = type.getMaxNegativeDays() != null
                        ? type.getMaxNegativeDays().negate()
                        : new BigDecimal("-999999");
                if (remaining.subtract(days).compareTo(floor) < 0) {
                    throw new BadRequestException(
                            "Request would exceed the maximum negative balance of "
                                    + type.getMaxNegativeDays() + " days for "
                                    + type.getCode());
                }
            } else if (remaining.compareTo(days) < 0) {
                throw new BadRequestException(
                        "Insufficient remaining balance: requested " + days
                                + ", available " + remaining);
            }
        }
        b.setReservedDays(b.getReservedDays().add(days));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        LeaveBalance saved = balances.save(b);
        ledger.record(saved.getEmployeeId(), saved.getLeaveTypeId(), saved.getYear(),
                LedgerTxType.RESERVATION, days.negate(), LocalDate.now(),
                "LEAVE_REQUEST", null, saved.remaining(),
                "Reserved for pending leave request", currentRequest.username());
        return saved;
    }

    /** reserved → used, on approval. */
    @Transactional
    public LeaveBalance commit(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days) {
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year);
        b.setReservedDays(b.getReservedDays().subtract(days).max(BigDecimal.ZERO));
        b.setUsedDays(b.getUsedDays().add(days));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        LeaveBalance saved = balances.save(b);
        ledger.record(saved.getEmployeeId(), saved.getLeaveTypeId(), saved.getYear(),
                LedgerTxType.LEAVE_TAKEN, days.negate(), LocalDate.now(),
                "LEAVE_APPROVED", null, saved.remaining(),
                "Leave request approved and committed", currentRequest.username());
        return saved;
    }

    /** Release a reservation on reject / cancel. */
    @Transactional
    public LeaveBalance release(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days) {
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year);
        b.setReservedDays(b.getReservedDays().subtract(days).max(BigDecimal.ZERO));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        LeaveBalance saved = balances.save(b);
        ledger.record(saved.getEmployeeId(), saved.getLeaveTypeId(), saved.getYear(),
                LedgerTxType.LEAVE_CANCELLED, days, LocalDate.now(),
                "LEAVE_CANCELLED", null, saved.remaining(),
                "Leave request cancelled, reservation released", currentRequest.username());
        return saved;
    }

    private LeaveBalance initialiseBalance(UUID employeeId, UUID leaveTypeId, int year) {
        LeaveType type = types.findById(leaveTypeId)
                .orElseThrow(() -> new BadRequestException("Leave type not found: " + leaveTypeId));
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(employeeId);
        b.setLeaveTypeId(leaveTypeId);
        b.setYear(year);
        BigDecimal entitlementDays = type.getDefaultAnnualEntitlementDays() == null
                ? BigDecimal.ZERO
                : type.getDefaultAnnualEntitlementDays();
        b.setEntitlementDays(entitlementDays);
        LeaveBalance saved = balances.save(b);
        ledger.record(saved.getEmployeeId(), saved.getLeaveTypeId(), saved.getYear(),
                LedgerTxType.OPENING, entitlementDays, LocalDate.now(),
                "BALANCE_INIT", null, saved.remaining(),
                "Initial balance created", "system");
        return saved;
    }
}
