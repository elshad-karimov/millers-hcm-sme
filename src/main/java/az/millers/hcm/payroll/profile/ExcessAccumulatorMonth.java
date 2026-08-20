package az.millers.hcm.payroll.profile;

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
import org.hibernate.annotations.TenantId;

/**
 * One month's contribution to a balancing period — the audit trail behind a
 * settlement.
 *
 * <p>{@code runningBalance} is stored rather than recomputed so a settled
 * amount can always be traced to the months that produced it, even if a norm or
 * a timesheet is later corrected.
 */
@Entity
@Table(name = "excess_accumulator_month", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class ExcessAccumulatorMonth {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "accumulator_id", nullable = false)
    private UUID accumulatorId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "actual_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualHours;

    @Column(name = "norm_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal normHours;

    @Column(name = "delta_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal deltaHours;

    @Column(name = "running_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal runningBalance;

    /** The category codes this row was summed from — traceability after a reconfig. */
    @Column(name = "categories_used", columnDefinition = "text")
    private String categoriesUsed;

    /** PERIOD_LOCK, REPOST or MANUAL. */
    @Column(nullable = false, length = 24)
    private String source = "MANUAL";

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "recorded_by")
    private String recordedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }
}
