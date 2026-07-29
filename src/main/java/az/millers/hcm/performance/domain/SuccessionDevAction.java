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
 * HCM_16 M415 — link nomination to development plan (replacement chart seam).
 * Shows how each successor is being prepared for their critical position.
 */
@Entity
@Table(name = "succession_dev_action", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class SuccessionDevAction {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "nomination_id", nullable = false)
    private UUID nominationId;

    @Column(name = "dev_plan_id", nullable = false)
    private UUID devPlanId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
