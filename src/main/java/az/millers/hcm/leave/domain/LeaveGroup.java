package az.millers.hcm.leave.domain;

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
import org.hibernate.annotations.TenantId;

/**
 * A logical grouping of employees that share leave entitlement policies
 * (M66 / P1-08). Examples: STANDARD, MANAGEMENT, UNION_MEMBERS.
 *
 * <p>Per-(group, leave_type) overrides live in {@link LeaveGroupEntitlement}.
 * When no override exists the {@link LeaveType}'s own defaults apply — so
 * orgs that don't need group differentiation can ignore this table entirely
 * and continue using the global {@code LeaveType} entitlements.
 *
 * <p>Exactly one row may have {@code isDefault = true} at any time
 * (enforced by {@code uq_leave_group_default} partial unique index in V54).
 * An employee with {@code leave_group_id = NULL} resolves to whichever
 * group has {@code isDefault = true}.
 */
@Entity
@Table(name = "leave_group", schema = "leave_mgmt")
@Getter
@Setter
@NoArgsConstructor
public class LeaveGroup {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultGroup = false;

    @Column(nullable = false)
    private boolean active = true;

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
