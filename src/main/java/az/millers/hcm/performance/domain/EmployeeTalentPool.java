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
import org.hibernate.annotations.TenantId;

/**
 * HCM_15 M410 — employee talent pools (PRD §15.5). Grouping sets for active
 * employees: HiPo cohorts, succession bench, mobility/relocation registry,
 * leadership pipeline. Distinct from recruitment candidate pools (M87).
 * HR-only visibility (GLOBAL RULE 17).
 */
@Entity
@Table(name = "employee_talent_pool", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeTalentPool {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String purpose;

    @Column(name = "owner_employee_id")
    private UUID ownerEmployeeId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
