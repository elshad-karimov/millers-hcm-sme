package az.millers.hcm.workflow.domain;

/**
 * M126 — what the SLA scheduler does when a workflow step breaches its
 * deadline.
 *
 * <p>Phase 1 implements {@link #NOTIFY} only. The other constants are
 * reserved so a future engine pass can plug in REASSIGN to a backup
 * approver, AUTO_APPROVE for low-risk paths, etc., without a schema
 * change.
 */
public enum EscalationAction {
    NOTIFY,
    REASSIGN,
    AUTO_APPROVE,
    AUTO_REJECT
}
