package az.millers.hcm.permission.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "permission_balance", schema = "permission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "permission_type_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
public class PermissionBalance {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "permission_type_id", nullable = false)
    private UUID permissionTypeId;

    @Column(nullable = false)
    private int year;

    @Column(name = "limit_hours", nullable = false, precision = 7, scale = 2)
    private BigDecimal limitHours = BigDecimal.ZERO;

    @Column(name = "adjustment_hours", nullable = false, precision = 7, scale = 2)
    private BigDecimal adjustmentHours = BigDecimal.ZERO;

    @Column(name = "used_hours", nullable = false, precision = 7, scale = 2)
    private BigDecimal usedHours = BigDecimal.ZERO;

    @Column(name = "reserved_hours", nullable = false, precision = 7, scale = 2)
    private BigDecimal reservedHours = BigDecimal.ZERO;

    @Column(name = "last_recalculated_at", nullable = false)
    private OffsetDateTime lastRecalculatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (lastRecalculatedAt == null) lastRecalculatedAt = OffsetDateTime.now();
    }

    public BigDecimal remaining() {
        return limitHours.add(adjustmentHours).subtract(usedHours).subtract(reservedHours);
    }
}
