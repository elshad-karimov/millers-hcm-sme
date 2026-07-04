package az.millers.hcm.workflow.domain;

/** Lifecycle of a {@link WorkflowInstance}. */
public enum WorkflowStatus {
    PENDING,
    APPROVED,
    REJECTED,
    RETURNED,
    CANCELLED,
    AUTO_APPROVED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
