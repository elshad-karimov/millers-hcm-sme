package az.millers.hcm.attendance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M331: Per-employee attendance impact row for a payroll run.
 * Written by {@link az.millers.hcm.attendance.service.AttendancePayrollSummaryService}
 * after PayrollEngine.calculate() completes, giving HR a pre-approval view of
 * which deductions attendance drove for each employee.
 */
@Entity
@Table(schema = "attendance", name = "attendance_payroll_summary")
@Getter
@Setter
@NoArgsConstructor
public class AttendancePayrollSummary {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "payroll_run_id", nullable = false)
    private UUID payrollRunId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "period_year", nullable = false)
    private short periodYear;

    @Column(name = "period_month", nullable = false)
    private short periodMonth;

    @Column(name = "total_late_minutes", nullable = false)
    private int totalLateMinutes;

    @Column(name = "total_early_minutes", nullable = false)
    private int totalEarlyMinutes;

    @Column(name = "total_absent_days", nullable = false)
    private int totalAbsentDays;

    @Column(name = "total_overtime_minutes", nullable = false)
    private int totalOvertimeMinutes;

    @Column(name = "late_deduction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal lateDeductionAmount = BigDecimal.ZERO;

    @Column(name = "early_leave_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal earlyLeaveDeduction = BigDecimal.ZERO;

    @Column(name = "absence_deduction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal absenceDeductionAmount = BigDecimal.ZERO;

    @Column(name = "total_deduction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeductionAmount = BigDecimal.ZERO;

    @Column(name = "policy_code", length = 50)
    private String policyCode;

    @Column(name = "policy_applied", nullable = false)
    private boolean policyApplied;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (computedAt == null) computedAt = OffsetDateTime.now();
    }
}
