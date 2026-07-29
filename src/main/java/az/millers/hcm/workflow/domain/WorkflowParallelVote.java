package az.millers.hcm.workflow.domain;

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
 * Records a single voter's decision on one parallel-gate step (M172 / PRD §9.1).
 *
 * <p>Unique per {@code (instance_id, step_id)} — each voter casts exactly one
 * vote per parallel step per instance. Once all parallel steps at a given
 * {@code step_order} have an APPROVE vote the gate passes.
 */
@Entity
@Table(name = "workflow_parallel_vote", schema = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowParallelVote {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 255)
    private String voter;

    @Column(name = "voted_at", nullable = false)
    private OffsetDateTime votedAt;

    @Column(nullable = false)
    private boolean approved;

    @Column(columnDefinition = "text")
    private String comment;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (votedAt == null) votedAt = OffsetDateTime.now();
    }
}
