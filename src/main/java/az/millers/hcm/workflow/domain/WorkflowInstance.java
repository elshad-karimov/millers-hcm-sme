package az.millers.hcm.workflow.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "workflow_instance", schema = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowInstance {

    @Id
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "definition_code", nullable = false)
    private String definitionCode;

    @Column(name = "subject_module", nullable = false)
    private String subjectModule;

    @Column(name = "subject_entity", nullable = false)
    private String subjectEntity;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(nullable = false)
    private String title;

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex;

    @Column(name = "current_step_role")
    private String currentStepRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status;

    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private OffsetDateTime initiatedAt;

    /**
     * M126 — wall-clock when the instance most recently moved to its
     * current step. Set at {@code start()}, bumped on every
     * step-advance. Drives the SLA scheduler's "how long has this been
     * waiting?" check.
     */
    @Column(name = "current_step_entered_at")
    private OffsetDateTime currentStepEnteredAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * M162 — set when a DELEGATE action is taken; stores the username of the
     * person this step has been explicitly handed off to.  Cleared when the
     * step advances or the instance reaches a terminal state.
     */
    @Column(name = "delegated_to")
    private String delegatedTo;

    /**
     * M172 — non-null only during a parallel gate. Contains the
     * {@code approverRole} values of parallel steps at the current
     * {@link #currentStepIndex} that have NOT yet received an APPROVE vote.
     * Cleared (set to null) when the gate passes and the instance advances.
     */
    @Array(length = 32)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "current_step_roles", columnDefinition = "text[]")
    private String[] currentStepRoles;

    /**
     * M174 — counts how many times this instance has been returned to the
     * initiator and re-submitted. No hard cap at the DB level; the service
     * applies an advisory limit of 10 re-submissions.
     */
    @Column(name = "resubmit_count", nullable = false)
    private int resubmitCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String payload;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (initiatedAt == null) initiatedAt = OffsetDateTime.now();
    }
}
