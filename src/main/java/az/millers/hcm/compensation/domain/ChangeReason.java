package az.millers.hcm.compensation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M360 — Change reason: explains why a compensation change occurred.
 * Used for audit trails and workflow routing.
 */
@Entity
@Table(name = "change_reason", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class ChangeReason {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "affects_workflow", nullable = false)
    private Boolean affectsWorkflow = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ChangeReasonCategory category;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
