package az.millers.hcm.workflow.domain;

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

@Entity
@Table(name = "workflow_action", schema = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowAction {

    @Id
    private UUID id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "step_name")
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType action;

    @Column(nullable = false)
    private String actor;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
