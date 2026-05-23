package az.millers.hcm.organization.domain;

/**
 * Hierarchy levels supported by the org chart (PRD 8.2.1).
 *
 * Company → Branch → Division → Department → Section → Unit → Team.
 * Position and Employee live in their own modules (Staffing, Core HR).
 */
public enum OrgUnitType {
    COMPANY,
    BRANCH,
    DIVISION,
    DEPARTMENT,
    SECTION,
    UNIT,
    TEAM
}
