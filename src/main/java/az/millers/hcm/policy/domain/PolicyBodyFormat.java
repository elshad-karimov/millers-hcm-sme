package az.millers.hcm.policy.domain;

/**
 * M138 — how the policy body is delivered. Mirrors V96 CHECK.
 */
public enum PolicyBodyFormat {
    /** Inline markdown — rendered client-side. */
    MARKDOWN,
    /** Inline HTML — rendered client-side (sanitised before display). */
    HTML,
    /** {@code attachment_url} points at a PDF in MinIO. */
    PDF,
    /** {@code attachment_url} is an external URL the SPA opens in a new tab. */
    URL
}
