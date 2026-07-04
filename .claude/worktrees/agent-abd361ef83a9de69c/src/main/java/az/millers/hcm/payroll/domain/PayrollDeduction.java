package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A per-employee payroll deduction applied automatically by the
 * {@link PayrollEngine} during each payroll run (M181 / PRD §8.9.1).
 *
 * <h3>Types</h3>
 * <ul>
 *   <li>{@code ONE_OFF}          — deducted once in the first eligible period, then COMPLETED.</li>
 *   <li>{@code RECURRING}        — deducted every eligible period until end_period or CANCELLED.</li>
 *   <li>{@code GARNISHMENT}      — same cadence as RECURRING; labelled for compliance reports.</li>
 *   <li>{@code ADVANCE_RECOVERY} — installment recovery of a salary advance; each period deducts
 *       {@code amountPerPeriod} until {@code recoveredAmount >= totalAmount}, then COMPLETED.</li>
 * </ul>
 */
@Entity
@Table(name = "payroll_deduction", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollDeduction {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "deduction_type", nullable = false, length = 30)
    private String deductionType;

    @Column(nullable = false)
    private String description;

    @Column(name = "amount_per_period", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPerPeriod;

    /** Total principal for ADVANCE_RECOVERY; null for other types. */
    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /** Running sum of amounts already applied across past payroll runs. */
    @Column(name = "recovered_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    @Column(name = "start_period_year", nullable = false)
    private int startPeriodYear;

    @Column(name = "start_period_month", nullable = false)
    private int startPeriodMonth;

    /** Inclusive end period; null means open-ended. */
    @Column(name = "end_period_year")
    private Integer endPeriodYear;

    @Column(name = "end_period_month")
    private Integer endPeriodMonth;

    /** ACTIVE | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(columnDefinition = "text")
    private String note;

    /** M343 — leave request that originated this deduction (nullable for manual deductions). */
    @Column(name = "source_leave_request_id")
    private UUID sourceLeaveRequestId;

    /** M343 — number of unpaid leave days this deduction represents. */
    @Column(name = "source_leave_days", precision = 8, scale = 2)
    private BigDecimal sourceLeaveDays;

    @Column(name = "created_by")
    private String createdBy;

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
