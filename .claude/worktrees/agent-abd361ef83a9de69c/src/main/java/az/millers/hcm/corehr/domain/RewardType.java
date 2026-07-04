package az.millers.hcm.corehr.domain;

/**
 * Category of recognition / award captured in {@link EmployeeReward}
 * (M72 / P2-18). Distinct from {@code payroll.payroll_bonus} — those are
 * accounted payments through the payroll engine; rewards here are records
 * of recognition events.
 */
public enum RewardType {
    RECOGNITION,
    MONETARY_AWARD,
    CERTIFICATE,
    APPRECIATION_LETTER,
    PROMOTION_FAST_TRACK,
    BONUS_RECOMMENDATION,
    ACHIEVEMENT,
    SPOT_AWARD,
    OTHER
}
