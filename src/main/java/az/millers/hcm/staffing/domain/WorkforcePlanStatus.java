package az.millers.hcm.staffing.domain;

/** Workforce plan lifecycle (M247 / PRD §42). Same shape as M245 staffing table. */
public enum WorkforcePlanStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    ACTIVE,
    REJECTED,
    ARCHIVED;

    public boolean isEditable() { return this == DRAFT; }
    public boolean isTerminal() { return this == ARCHIVED; }
}
