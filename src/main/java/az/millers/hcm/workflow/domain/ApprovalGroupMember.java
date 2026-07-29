package az.millers.hcm.workflow.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M443 — Member of an approval group.
 */
@Entity
@Table(name = "approval_group_member", schema = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalGroupMember {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
