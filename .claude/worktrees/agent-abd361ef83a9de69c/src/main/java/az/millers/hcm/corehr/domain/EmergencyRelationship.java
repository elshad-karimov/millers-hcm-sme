package az.millers.hcm.corehr.domain;

/**
 * Relationship between an employee and an emergency contact
 * ({@link EmployeeEmergencyContact}, M63 / P1-06).
 */
public enum EmergencyRelationship {
    SPOUSE, CHILD, PARENT, SIBLING, GUARDIAN, FRIEND, OTHER
}
