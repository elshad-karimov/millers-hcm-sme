package az.millers.hcm.corehr.domain;

/**
 * Family relationship between an employee and a {@link EmployeeDependent}
 * (M71 / P2-03). Distinct from {@link EmergencyRelationship} — emergency
 * contacts are about who to call in a crisis; dependents are about benefits,
 * insurance enrolment, parental leave eligibility.
 */
public enum DependentRelationship {
    SPOUSE, CHILD, PARENT, SIBLING, GUARDIAN, OTHER
}
