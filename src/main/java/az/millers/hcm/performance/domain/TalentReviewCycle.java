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
 * HCM_15 M411 — talent review cycle (PRD §15.7). Annual/bi-annual talent
 * calibration cycle where HR+exec panels place employees on the 9-box and
 * designate HiPo/retention status. HR-only visibility (GLOBAL RULE 17).
 */
@Entity
@Table(name = "talent_review_cycle", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class TalentReviewCycle {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int year;

    /** DRAFT | ACTIVE | COMPLETED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

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
