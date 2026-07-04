package az.millers.hcm.performance.domain;

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
 * HCM_15 M410 — talent pool membership (PRD §15.6). Each employee can appear in
 * multiple pools. When a pool is deleted all its members are cascade-removed.
 */
@Entity
@Table(name = "employee_pool_member", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class EmployeePoolMember {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "pool_id", nullable = false)
    private UUID poolId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "added_by", length = 80)
    private String addedBy;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    @Column(length = 500)
    private String note;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (addedAt == null) addedAt = OffsetDateTime.now();
    }
}
