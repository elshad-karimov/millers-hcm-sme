package az.millers.hcm.staffing.domain;

/** Workflow status for a replacement request (M246 / PRD §16). */
public enum ReplacementStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    COMPLETED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }
}
