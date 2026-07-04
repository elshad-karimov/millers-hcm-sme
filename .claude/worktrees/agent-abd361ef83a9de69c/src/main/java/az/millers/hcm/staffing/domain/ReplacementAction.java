package az.millers.hcm.staffing.domain;

/** What to do with a seat once its occupant leaves (M246 / PRD §16). */
public enum ReplacementAction {
    /** Default — post a vacancy and recruit. */
    OPEN_RECRUITMENT,
    /** Replacement is already identified internally. */
    INTERNAL_TRANSFER,
    /** Assign an acting / interim occupant. */
    ACTING,
    /** Freeze the position; do not recruit. */
    FREEZE,
    /** Close the position permanently. */
    CLOSE
}
