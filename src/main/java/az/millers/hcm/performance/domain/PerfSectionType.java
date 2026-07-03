package az.millers.hcm.performance.domain;

/** HCM_12 M389 — review-template section types (PRD §5.2). */
public enum PerfSectionType {
    GOALS,
    KPI,
    OKR,
    COMPETENCY,
    VALUES,
    BEHAVIORAL,
    MANAGER_COMMENTS,
    EMPLOYEE_COMMENTS,
    DEVELOPMENT_PLAN,
    FINAL_RATING,
    PROMOTION_RECOMMENDATION,
    COMPENSATION_RECOMMENDATION,
    SUMMARY,
    SIGNATURE;

    /** Sections that carry weight in the §18.2 overall score. */
    public boolean isScoring() {
        return this == GOALS || this == KPI || this == OKR || this == COMPETENCY
                || this == VALUES || this == BEHAVIORAL;
    }
}
