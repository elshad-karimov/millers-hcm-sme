package az.millers.hcm.payroll.timepay;

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
 * Norm working hours for a period — the divisor behind every hourly rate.
 *
 * <p>Dated rather than constant: the workbook keeps it in one cell (151 for
 * January 2026) and it changes with the production calendar.
 */
@Entity
@Table(name = "period_norm_hours", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PeriodNormHours {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "norm_hours", nullable = false, precision = 8, scale = 2)
    private BigDecimal normHours;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
