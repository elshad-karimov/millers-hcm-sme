package az.millers.hcm.recruitment.domain;

/**
 * M289 — Recruitment PRD §15: the answer shape of a knockout / screening
 * question, which drives both the candidate input control and the
 * pass/fail rule that {@code ScreeningQuestionService} applies.
 */
public enum ScreeningQuestionType {
    /** Yes/no — passing answer is {@code expectedBoolean} (null = any). */
    BOOLEAN,
    /** One of {@code options} — passing values are {@code acceptableOptions}. */
    SINGLE_CHOICE,
    /** A number — passing range is the inclusive {@code [minValue, maxValue]}. */
    NUMERIC
}
