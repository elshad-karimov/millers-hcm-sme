package az.millers.hcm.engagement.domain;

/**
 * Shape of a survey question (M116).
 *
 * <ul>
 *   <li>{@link #RATING_1_5} — classic 1-5 satisfaction / agreement scale.</li>
 *   <li>{@link #RATING_1_10} — 0-10 NPS scale ({@code 9-10 promoters},
 *       {@code 0-6 detractors}).</li>
 *   <li>{@link #BOOLEAN} — yes/no. Stored as {@code rating_value} 0 or 1.</li>
 *   <li>{@link #TEXT} — free-text response.</li>
 *   <li>{@link #MULTIPLE_CHOICE} — pick one option from a list in
 *       {@code metadata.options}.</li>
 * </ul>
 */
public enum QuestionType {
    RATING_1_5,
    RATING_1_10,
    BOOLEAN,
    TEXT,
    MULTIPLE_CHOICE;

    /** True iff this type stores its answer in {@code rating_value}. */
    public boolean isRating() {
        return this == RATING_1_5 || this == RATING_1_10 || this == BOOLEAN;
    }
}
