package az.millers.hcm.recruitment.domain;

/** M274 / Recruitment PRD §4 — what kind of hire this requisition represents. */
public enum RequisitionType {
    NEW_HEADCOUNT,
    REPLACEMENT,
    TEMPORARY,
    PROJECT,
    SEASONAL,
    INTERNSHIP,
    CONTRACTOR,
    MASS_HIRING,
    EXECUTIVE,
    INTERNAL;

    /**
     * M275 hook — replacement requisitions get the shorter approval
     * chain (the headcount already exists); everything else goes
     * through the full new-headcount chain.
     */
    public boolean isReplacementLike() {
        return this == REPLACEMENT || this == INTERNAL;
    }
}
