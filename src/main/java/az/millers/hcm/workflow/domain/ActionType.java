package az.millers.hcm.workflow.domain;

/** User actions on a running workflow instance (PRD 9.3). */
public enum ActionType {
    START,
    APPROVE,
    REJECT,
    RETURN,
    COMMENT,
    CANCEL,
    AUTO_APPROVE
}
