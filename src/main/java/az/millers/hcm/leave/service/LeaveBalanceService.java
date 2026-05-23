package az.millers.hcm.leave.service;

import java.math.BigDecimal;
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
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;

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

    public LeaveBalanceService(LeaveBalanceRepository balances,
                               LeaveTypeRepository types,
                               AuditService audit) {
        this.balances = balances;
        this.types = types;
        this.audit = audit;
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
        return saved;
    }

    /** Called by {@code LeaveRequestService} when a request is submitted. */
    @Transactional
    public LeaveBalance reserve(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days,
                                 boolean enforceLimit) {
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year);
        if (enforceLimit && b.remaining().compareTo(days) < 0) {
            throw new BadRequestException(
                    "Insufficient remaining balance: requested " + days
                            + ", available " + b.remaining());
        }
        b.setReservedDays(b.getReservedDays().add(days));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        return balances.save(b);
    }

    /** reserved → used, on approval. */
    @Transactional
    public LeaveBalance commit(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days) {
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year);
        b.setReservedDays(b.getReservedDays().subtract(days).max(BigDecimal.ZERO));
        b.setUsedDays(b.getUsedDays().add(days));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        return balances.save(b);
    }

    /** Release a reservation on reject / cancel. */
    @Transactional
    public LeaveBalance release(UUID employeeId, UUID leaveTypeId, int year, BigDecimal days) {
        LeaveBalance b = ensureBalance(employeeId, leaveTypeId, year);
        b.setReservedDays(b.getReservedDays().subtract(days).max(BigDecimal.ZERO));
        b.setLastRecalculatedAt(OffsetDateTime.now());
        return balances.save(b);
    }

    private LeaveBalance initialiseBalance(UUID employeeId, UUID leaveTypeId, int year) {
        LeaveType type = types.findById(leaveTypeId)
                .orElseThrow(() -> new BadRequestException("Leave type not found: " + leaveTypeId));
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(employeeId);
        b.setLeaveTypeId(leaveTypeId);
        b.setYear(year);
        b.setEntitlementDays(
                type.getDefaultAnnualEntitlementDays() == null
                        ? BigDecimal.ZERO
                        : type.getDefaultAnnualEntitlementDays());
        return balances.save(b);
    }
}
