package az.millers.hcm.staffing.domain;

/** M260 / PRD §40 — Workflow status for a position transfer request. */
public enum TransferStatus {
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
