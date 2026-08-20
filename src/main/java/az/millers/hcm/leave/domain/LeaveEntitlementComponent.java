package az.millers.hcm.leave.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M151 — one addend of an employee's annual leave entitlement for one leave
 * type and year.
 *
 * <p>These rows explain {@link LeaveBalance#getEntitlementDays()}: their sum
 * is written into it by {@code LeaveEntitlementComponentService}. They never
 * move a balance by themselves, so a wrong component is visible and
 * correctable before it can affect anyone's leave.
 *
 * <p>{@link EntitlementComponentSource#DERIVED} rows are owned by the
 * resolvers and rewritten on each recalculation;
 * {@link EntitlementComponentSource#MANUAL} rows are HR's and are never
 * overwritten.
 */
@Entity
@Table(name = "leave_entitlement_component", schema = "leave_mgmt",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"employee_id", "leave_type_id", "year", "component_code"}))
@Getter
@Setter
@NoArgsConstructor
public class LeaveEntitlementComponent {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_code", nullable = false, length = 32)
    private EntitlementComponentCode componentCode;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal days = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntitlementComponentSource source = EntitlementComponentSource.DERIVED;

    /**
     * Human-readable justification — "Specialist", "8.5 yrs professional
     * experience → 5–10 bracket", "2 children under 14". Rendered on the
     * breakdown and in audit exports so the reasoning is legible without
     * reading code.
     */
    @Column(columnDefinition = "text")
    private String basis;

    /** When a DERIVED row was last recomputed. Null on MANUAL rows. */
    @Column(name = "computed_at")
    private OffsetDateTime computedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

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
