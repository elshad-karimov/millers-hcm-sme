package az.millers.hcm.attendance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.domain.AttendancePolicy;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;
import az.millers.hcm.attendance.repo.DailySummaryRepository;

/**
 * M327: Computes attendance-based salary deductions for a payroll period.
 *
 * <p>Formula (per decision 2026-06-26):
 * <ul>
 *   <li>Per-minute rate  = baseSalary / (absenceDailyDivisor × hoursPerDay × 60)</li>
 *   <li>Late deduction   = perMinuteRate × max(0, totalLateMin − monthlyThreshold)</li>
 *   <li>Early deduction  = perMinuteRate × max(0, totalEarlyMin − tolerance) when
 *       policy.earlyLeaveTreatment = SALARY_DEDUCTION</li>
 *   <li>Absence deduction = (baseSalary / absenceDailyDivisor) × absentDays</li>
 * </ul>
 *
 * <p>Each deduction type is gated by its enabled flag on the employee's policy.
 * When no policy exists the result carries zero deductions so the engine
 * applies no attendance adjustment.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceDeductionBridge {

    private final DailySummaryRepository summaries;
    private final AttendancePolicyService policyService;

    public AttendanceDeductionBridge(DailySummaryRepository summaries,
                                      AttendancePolicyService policyService) {
        this.summaries = summaries;
        this.policyService = policyService;
    }

    public record AttendanceDeductionResult(
            int totalLateMinutes,
            int totalEarlyMinutes,
            int totalAbsentDays,
            int totalOvertimeMinutes,
            BigDecimal lateDeduction,
            BigDecimal earlyLeaveDeduction,
            BigDecimal absenceDeduction,
            BigDecimal totalDeduction,
            boolean policyApplied,
            String policyCode) {

        static AttendanceDeductionResult noPolicy(
                int lateMin, int earlyMin, int absentDays, int otMin) {
            return new AttendanceDeductionResult(
                    lateMin, earlyMin, absentDays, otMin,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    false, null);
        }
    }

    public AttendanceDeductionResult compute(UUID employeeId, int year, int month,
                                              BigDecimal baseSalary) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        List<DailySummary> periodSummaries = summaries
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to);

        int totalLateMinutes = 0;
        int totalEarlyMinutes = 0;
        int totalAbsentDays = 0;
        int totalOvertimeMinutes = 0;
        for (DailySummary s : periodSummaries) {
            totalLateMinutes += s.getLateMinutes();
            totalEarlyMinutes += s.getEarlyMinutes();
            totalOvertimeMinutes += s.getOvertimeMinutes();
            if (s.getStatus() == SummaryStatus.ABSENT) {
                totalAbsentDays++;
            }
        }

        AttendancePolicy policy = policyService.findForEmployee(employeeId).orElse(null);
        if (policy == null) {
            return AttendanceDeductionResult.noPolicy(
                    totalLateMinutes, totalEarlyMinutes, totalAbsentDays, totalOvertimeMinutes);
        }

        int minutesPerMonth = policy.minutesPerMonth();
        if (minutesPerMonth <= 0) {
            return AttendanceDeductionResult.noPolicy(
                    totalLateMinutes, totalEarlyMinutes, totalAbsentDays, totalOvertimeMinutes);
        }

        BigDecimal perMinuteRate = baseSalary
                .divide(BigDecimal.valueOf(minutesPerMonth), 10, RoundingMode.HALF_UP);

        BigDecimal lateDeduction = BigDecimal.ZERO;
        if (policy.isLateDeductionEnabled()) {
            int deductibleMinutes = Math.max(0,
                    totalLateMinutes - policy.getLateMonthlyThresholdMinutes());
            if (deductibleMinutes > 0) {
                lateDeduction = perMinuteRate.multiply(BigDecimal.valueOf(deductibleMinutes))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal earlyLeaveDeduction = BigDecimal.ZERO;
        if (policy.isEarlyLeaveDeductionEnabled()
                && "SALARY_DEDUCTION".equals(policy.getEarlyLeaveTreatment())) {
            int deductibleMinutes = Math.max(0,
                    totalEarlyMinutes - policy.getEarlyLeaveToleranceMinutes());
            if (deductibleMinutes > 0) {
                earlyLeaveDeduction = perMinuteRate.multiply(BigDecimal.valueOf(deductibleMinutes))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal absenceDeduction = BigDecimal.ZERO;
        if (policy.isAbsenceDeductionEnabled() && totalAbsentDays > 0) {
            BigDecimal dailyRate = baseSalary.divide(
                    BigDecimal.valueOf(policy.getAbsenceDailyDivisor()), 10, RoundingMode.HALF_UP);
            absenceDeduction = dailyRate.multiply(BigDecimal.valueOf(totalAbsentDays))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalDeduction = lateDeduction.add(earlyLeaveDeduction).add(absenceDeduction);

        return new AttendanceDeductionResult(
                totalLateMinutes, totalEarlyMinutes, totalAbsentDays, totalOvertimeMinutes,
                lateDeduction, earlyLeaveDeduction, absenceDeduction, totalDeduction,
                true, policy.getCode());
    }
}
