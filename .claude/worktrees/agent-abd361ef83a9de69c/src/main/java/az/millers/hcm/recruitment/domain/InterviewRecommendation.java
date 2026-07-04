package az.millers.hcm.recruitment.domain;

/**
 * Interviewer's qualitative call (M85). Distinct from the existing
 * {@link Recommendation} which is the offer-stage recommendation.
 */
public enum InterviewRecommendation {
    STRONG_HIRE,
    HIRE,
    MAYBE,
    NO_HIRE,
    STRONG_NO_HIRE
}
