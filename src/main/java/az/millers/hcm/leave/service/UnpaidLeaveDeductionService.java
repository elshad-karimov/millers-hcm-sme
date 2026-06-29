package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.leave.api.dto.UnpaidDeductionSyncResult;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveRequestStatus;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.PayrollDeductionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M343 — syncs approved unpaid-leave requests for a pay period into
 * payroll.payroll_deduction ONE_OFF rows that the PayrollEngine picks up.
 *
 * <p>Formula: deduction = monthlyBaseSalary / workingDaysPerMonth × totalDays.
 * Default working days per month: 22.
 * Timing: HR-triggered before/during payroll run preparation.
 * Reversal: if an unpaid leave request is cancelled after the deduction was applied,
 * a new deduction row with negative amount is created (correction in next run).
 */
@Service
public class UnpaidLeaveDeductionService {

    private static final int DEFAULT_WORKING_DAYS = 22;
    private static final String DEDUCTION_TYPE = "UNPAID_LEAVE";

    private final LeaveRequestRepository leaveRequests;
    private final LeaveTypeRepository leaveTypes;
    private final PayrollDeductionRepository deductions;
    private final EmployeeCompensationRepository compensations;
    private final CurrentRequest currentRequest;

    public UnpaidLeaveDeductionService(LeaveRequestRepository leaveRequests,
                                        LeaveTypeRepository leaveTypes,
                                        PayrollDeductionRepository deductions,
                                        EmployeeCompensationRepository compensations,
                                        CurrentRequest currentRequest) {
        this.leaveRequests = leaveRequests;
        this.leaveTypes = leaveTypes;
        this.deductions = deductions;
        this.compensations = compensations;
        this.currentRequest = currentRequest;
    }

    /** Scans approved unpaid leave in [year, month] and creates ONE_OFF deduction rows. Idempotent. */
    @Transactional
    public UnpaidDeductionSyncResult syncPeriod(int year, int month, int workingDaysPerMonth) {
        if (workingDaysPerMonth <= 0) workingDaysPerMonth = DEFAULT_WORKING_DAYS;

        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        // All approved leave requests that overlap the period
        List<LeaveRequest> periodLeave = leaveRequests.findByStartDateBetween(
                periodStart.minusMonths(2), periodEnd) // cast a wider net then filter
                .stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED)
                .filter(r -> !r.getStartDate().isAfter(periodEnd) && !r.getEndDate().isBefore(periodStart))
                .collect(Collectors.toList());

        Map<UUID, LeaveType> typeMap = leaveTypes.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, t -> t));

        int created = 0;
        int skipped = 0;
        List<String> details = new ArrayList<>();

        int finalWorkingDays = workingDaysPerMonth;
        for (LeaveRequest req : periodLeave) {
            LeaveType type = typeMap.get(req.getLeaveTypeId());
            if (type == null || type.isPaid()) continue; // only UNPAID types

            // Idempotency: skip if a deduction row already exists for this leave request
            if (deductions.findBySourceLeaveRequestId(req.getId()).isPresent()) {
                skipped++;
                continue;
            }

            BigDecimal salary = compensations
                    .findActiveOn(req.getEmployeeId(), periodStart)
                    .map(c -> c.getMonthlyBaseSalary())
                    .orElse(BigDecimal.ZERO);

            if (salary.compareTo(BigDecimal.ZERO) == 0) {
                details.add("SKIP (no salary): employee=" + req.getEmployeeId() + " req=" + req.getId());
                skipped++;
                continue;
            }

            BigDecimal days = req.getTotalDays() != null ? req.getTotalDays() : BigDecimal.ONE;
            BigDecimal dailyRate = salary.divide(BigDecimal.valueOf(finalWorkingDays), 4, RoundingMode.HALF_UP);
            BigDecimal amount = dailyRate.multiply(days).setScale(2, RoundingMode.HALF_UP);

            PayrollDeduction d = new PayrollDeduction();
            d.setEmployeeId(req.getEmployeeId());
            d.setDeductionType(DEDUCTION_TYPE);
            d.setDescription("Unpaid leave " + req.getRequestNo() + " (" + type.getCode() + ") "
                    + req.getStartDate() + " – " + req.getEndDate());
            d.setAmountPerPeriod(amount);
            d.setStartPeriodYear(year);
            d.setStartPeriodMonth(month);
            d.setEndPeriodYear(year);
            d.setEndPeriodMonth(month);
            d.setSourceLeaveRequestId(req.getId());
            d.setSourceLeaveDays(days);
            d.setCreatedBy(currentRequest.username());
            d.setStatus("ACTIVE");
            deductions.save(d);
            created++;
            details.add("CREATED: employee=" + req.getEmployeeId() + " days=" + days + " amount=" + amount);
        }

        return new UnpaidDeductionSyncResult(year, month, finalWorkingDays, created, skipped, details);
    }

    /** Lists all UNPAID_LEAVE deduction rows for an employee. */
    @Transactional(readOnly = true)
    public List<PayrollDeduction> listForEmployee(UUID employeeId) {
        return deductions.findByEmployeeIdAndDeductionType(employeeId, DEDUCTION_TYPE);
    }

    /**
     * Reversal path: cancels a leave request's deduction by creating a negative ONE_OFF
     * row for the current period. The existing row is marked CANCELLED (not deleted).
     */
    @Transactional
    public void reverseForLeaveRequest(UUID leaveRequestId, int correctionYear, int correctionMonth) {
        deductions.findBySourceLeaveRequestId(leaveRequestId).ifPresent(existing -> {
            if ("CANCELLED".equals(existing.getStatus())) return;
            existing.setStatus("CANCELLED");
            deductions.save(existing);

            // Create a correction deduction (negative amount)
            PayrollDeduction correction = new PayrollDeduction();
            correction.setEmployeeId(existing.getEmployeeId());
            correction.setDeductionType(DEDUCTION_TYPE);
            correction.setDescription("REVERSAL: " + existing.getDescription());
            correction.setAmountPerPeriod(existing.getAmountPerPeriod().negate());
            correction.setStartPeriodYear(correctionYear);
            correction.setStartPeriodMonth(correctionMonth);
            correction.setEndPeriodYear(correctionYear);
            correction.setEndPeriodMonth(correctionMonth);
            correction.setCreatedBy(currentRequest.username());
            correction.setStatus("ACTIVE");
            deductions.save(correction);
        });
    }
}
