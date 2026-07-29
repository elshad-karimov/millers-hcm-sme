package az.millers.hcm.workflow.domain;

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

@Entity
@Table(name = "workflow_step", schema = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowStep {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false)
    private String name;

    /** Spring authority required to act on this step (e.g. {@code ROLE_HR_ADMIN}). */
    @Column(name = "approver_role", nullable = false)
    private String approverRole;

    /**
     * When {@code true}, the approver is the subject's direct manager
     * (via {@code core_hr.employee.manager_id}). {@link #approverRole}
     * is still required (the caller must have it) but no longer
     * sufficient — {@code WorkflowService} additionally checks that
     * the caller IS the resolved manager. PRD 9 / 14.9 — M35.
     */
    @Column(name = "resolves_to_manager", nullable = false)
    private boolean resolvesToManager;

    /**
     * M142 — when {@code true}, the approver is the subject employee's
     * HRBP (resolved via {@code org_unit.hrbp_id}, walking up the tree
     * when null). {@link #approverRole} is still required but not
     * sufficient — {@code WorkflowService} additionally checks the
     * caller IS the resolved HRBP.
     */
    @Column(name = "resolves_to_hrbp", nullable = false)
    private boolean resolvesToHrbp;

    @Column(name = "sla_hours")
    private Integer slaHours;

    /**
     * M126 — what the SLA scheduler does on a breach.
     * Phase 1 implements {@link EscalationAction#NOTIFY}; the others
     * are reserved.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_action", nullable = false, length = 32)
    private EscalationAction escalationAction = EscalationAction.NOTIFY;

    /**
     * M126 — username or role name to receive the breach notification.
     * Null falls back to the step's {@link #approverRole}.
     */
    @Column(name = "escalation_to", length = 160)
    private String escalationTo;

    /**
     * M172 — when {@code true} this step is part of a <em>parallel gate</em>
     * at this {@link #stepOrder}. All {@code workflow_step} rows with the same
     * {@code step_order} and {@code parallel=true} must each receive an APPROVE
     * vote before the instance advances. Sequential steps (default) are
     * unaffected.
     */
    @Column(nullable = false)
    private boolean parallel;

    /**
     * M172 — optional Spring SpEL expression evaluated against the
     * {@code workflow_instance.payload} JSON map. When the expression returns
     * {@code false} (or cannot be resolved), the step is skipped automatically.
     * Null means the step is always active.
     */
    @Column(name = "condition_spel", length = 500)
    private String conditionSpel;

    /**
     * M443 — if set, eligible actors = group members (any may act).
     * Takes precedence over approverRole/resolvesToManager/resolvesToHrbp.
     */
    @Column(name = "approval_group_id")
    private UUID approvalGroupId;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
