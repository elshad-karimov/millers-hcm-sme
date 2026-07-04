package az.millers.hcm.performance.domain;

/** 180/360 feedback relationship to the subject (PRD 8.13.5). */
public enum FeedbackRelationship {
    PEER,
    DIRECT_REPORT,
    MANAGER,
    SKIP_LEVEL,
    CROSS_FUNCTIONAL,
    EXTERNAL,
    SELF
}
