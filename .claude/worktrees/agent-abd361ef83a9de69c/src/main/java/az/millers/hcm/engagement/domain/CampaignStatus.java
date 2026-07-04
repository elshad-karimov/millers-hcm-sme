package az.millers.hcm.engagement.domain;

/** Lifecycle of a {@link SurveyCampaign} (M116). */
public enum CampaignStatus {
    /** Editable; not yet visible to respondents. */
    DRAFT,
    /** Open for responses inside {@code [opens_on, closes_on]}. */
    ACTIVE,
    /** Past {@code closes_on} or admin-closed early. Read-only. */
    CLOSED,
    /** Pulled back without going live (e.g., template fixed). */
    CANCELLED
}
