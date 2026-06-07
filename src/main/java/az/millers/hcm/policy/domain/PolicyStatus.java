package az.millers.hcm.policy.domain;

/**
 * M138 — lifecycle status of a {@link PolicyDocument}. Mirrors the
 * V96 CHECK whitelist.
 */
public enum PolicyStatus {
    /** Editable. Service refuses to surface DRAFT rows on the self-service browse. */
    DRAFT,
    /** Live. Service rejects mutations to anything except transitioning to ARCHIVED. */
    PUBLISHED,
    /** Superseded. Read-only forever. */
    ARCHIVED
}
