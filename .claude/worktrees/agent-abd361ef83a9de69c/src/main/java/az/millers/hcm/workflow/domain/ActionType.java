package az.millers.hcm.workflow.domain;

/** User actions on a running workflow instance (PRD 9.3). */
public enum ActionType {
    START,
    APPROVE,
    REJECT,
    RETURN,
    COMMENT,
    CANCEL,
    AUTO_APPROVE,
    /** M162: explicit hand-off of the current step to a named user. */
    DELEGATE,
    /** M166: attach a supporting document to the current step (PRD §9.3). */
    ATTACH_DOCUMENT,
    /** M174: initiator re-submits a RETURNED instance after making corrections. */
    RESUBMIT
}
