package az.millers.hcm.compbenefits.domain;

/** HCM_11 M375 — coverage tier (who the plan covers), driving the contribution split. */
public enum BenefitCoverageTier {
    EMPLOYEE_ONLY,
    EMPLOYEE_SPOUSE,
    EMPLOYEE_CHILDREN,
    FAMILY,
    CUSTOM
}
