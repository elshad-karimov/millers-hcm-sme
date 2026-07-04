package az.millers.hcm.lifecycle.domain;

/**
 * Progressive-discipline action levels (M67 / P1-12).
 *
 * <p>Severity ordering is implicit in the enum order: VERBAL_WARNING (least
 * severe) → DISMISSAL (most severe). Service code uses {@link #isDismissalLevel()}
 * to route the more sensitive actions through an additional approval step.
 */
public enum DisciplinaryActionType {

    /** First-stage informal warning, often documented after a conversation. */
    VERBAL_WARNING,

    /** Formal written warning placed in the personnel file. */
    WRITTEN_WARNING,

    /** Last warning before dismissal — typically follows a written warning. */
    FINAL_WARNING,

    /** Monetary penalty (where statutorily permitted). */
    PENALTY,

    /** Temporary suspension from duties pending resolution. */
    SUSPENSION,

    /** Open investigation — no action taken yet, but conduct is on record. */
    INVESTIGATION,

    /** Termination for cause — requires the extra-approval step in the workflow. */
    DISMISSAL;

    public boolean isDismissalLevel() {
        return this == DISMISSAL;
    }
}
