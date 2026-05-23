package az.millers.hcm.compbenefits.domain;

/** How a bonus_run_item amount was derived (PRD 8.15.2). */
public enum BonusItemSource {
    /** Looked up in {@code bonus_matrix_rule} via recommendation or rating band. */
    MATRIX_LOOKUP,
    /** Used {@code review.bonusPercent} set explicitly during calibration. */
    REVIEW_OVERRIDE,
    /** Admin-entered override (item edited or added by hand). */
    MANUAL
}
