package az.millers.hcm.staffing.domain;

/**
 * Funding axis (M244 / PRD §21). Independent of {@link PositionStatus}
 * (lifecycle) and {@link VacancyState} (occupancy). A position can be
 * lifecycle-ACTIVE but funding-UNFUNDED — in that case the recruitment
 * gate blocks any new hires until funding lands.
 */
public enum FundingStatus {
    /** No funding allocated. Default for new positions. Blocks recruitment. */
    UNFUNDED,
    /** Funding request submitted; awaiting approval. Blocks recruitment. */
    PENDING,
    /** Some seats funded, others not. Recruitment allowed but capped. */
    PARTIALLY_FUNDED,
    /** Fully funded. Recruitment allowed. */
    FUNDED,
    /** Grant / project funding lapsed. Blocks recruitment. */
    EXPIRED;

    /** True if recruitment + new fills are permitted. */
    public boolean allowsRecruitment() {
        return this == FUNDED || this == PARTIALLY_FUNDED;
    }
}
