package az.millers.hcm.organization.domain;

/**
 * M143 — Well-known org-unit type codes (§5).
 *
 * <p>These are the seven types seeded into {@code organization.org_unit_type}
 * by migration V101. Operators may add further types at runtime via the
 * admin UI; this class exists only to give callers compile-time constants
 * for the built-in set and to serve as the canonical documentation of their
 * meaning.
 *
 * <p>All field types that previously used this class as an enum now use
 * {@code String}, validated at runtime against the config table.
 */
public final class OrgUnitType {

    public static final String COMPANY    = "COMPANY";
    public static final String BRANCH     = "BRANCH";
    public static final String DIVISION   = "DIVISION";
    public static final String DEPARTMENT = "DEPARTMENT";
    public static final String SECTION    = "SECTION";
    public static final String UNIT       = "UNIT";
    public static final String TEAM       = "TEAM";

    private OrgUnitType() {}
}
