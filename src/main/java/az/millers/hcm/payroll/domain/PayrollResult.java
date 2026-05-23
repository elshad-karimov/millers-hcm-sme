package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payroll_result", schema = "payroll",
        uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollResult {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "payslip_no", nullable = false, unique = true)
    private String payslipNo;

    @Column(name = "timesheet_id")
    private UUID timesheetId;

    @Column(name = "base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(name = "worked_hours", nullable = false, precision = 9, scale = 2)
    private BigDecimal workedHours = BigDecimal.ZERO;

    @Column(name = "expected_monthly_hours", nullable = false, precision = 9, scale = 2)
    private BigDecimal expectedMonthlyHours = BigDecimal.ZERO;

    @Column(name = "pro_ration_factor", nullable = false, precision = 6, scale = 4)
    private BigDecimal proRationFactor = BigDecimal.ONE;

    @Column(name = "overtime_hours", nullable = false, precision = 9, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "overtime_pay", nullable = false, precision = 14, scale = 2)
    private BigDecimal overtimePay = BigDecimal.ZERO;

    @Column(name = "bonus_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    @Column(name = "allowance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allowanceAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "income_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal incomeTax = BigDecimal.ZERO;

    @Column(name = "dsmf_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal dsmfEmployee = BigDecimal.ZERO;

    @Column(name = "dsmf_employer", nullable = false, precision = 14, scale = 2)
    private BigDecimal dsmfEmployer = BigDecimal.ZERO;

    @Column(name = "mmi_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal mmiEmployee = BigDecimal.ZERO;

    @Column(name = "mmi_employer", nullable = false, precision = 14, scale = 2)
    private BigDecimal mmiEmployer = BigDecimal.ZERO;

    @Column(name = "unempl_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal unemplEmployee = BigDecimal.ZERO;

    @Column(name = "unempl_employer", nullable = false, precision = 14, scale = 2)
    private BigDecimal unemplEmployer = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_details")
    private String calculationDetails;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
