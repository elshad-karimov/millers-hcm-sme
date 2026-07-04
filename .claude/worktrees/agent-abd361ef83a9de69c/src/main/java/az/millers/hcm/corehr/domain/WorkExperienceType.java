package az.millers.hcm.corehr.domain;

/**
 * Origin of a {@link EmployeeWorkExperience} row (M71 / P2-05).
 *
 * <p>EXTERNAL covers prior employment with other companies (recorded at hire
 * from a CV). INTERNAL covers roles held inside this company — auto-populated
 * from {@code lifecycle.contract_change} on promotions / transfers in a
 * future Phase 2 pass, allowing the work-history tab to render both lifetime
 * career events alongside imported pre-hire history.
 */
public enum WorkExperienceType {
    EXTERNAL,
    INTERNAL
}
