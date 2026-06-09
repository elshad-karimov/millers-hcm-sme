package az.millers.hcm.staffing.domain;

/**
 * Lifecycle states for a staffing table version (M245 / PRD §45).
 *
 * <p>Only ACTIVE versions are considered the legally-binding establishment
 * for a legal entity. Service-layer rules enforce that no two ACTIVE
 * staffing tables for the same legal entity overlap in time.
 */
public enum StaffingTableStatus {
    /** Being built / edited. Lines can be added, removed, edited. */
    DRAFT,
    /** Submitted for approval. Frozen for editing while awaiting decision. */
    PENDING_APPROVAL,
    /** Approved + currently in force. */
    ACTIVE,
    /** Rejected by approver; sent back to DRAFT. */
    REJECTED,
    /** Superseded by a later version, kept for history. */
    ARCHIVED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isTerminal() {
        return this == ARCHIVED;
    }
}
