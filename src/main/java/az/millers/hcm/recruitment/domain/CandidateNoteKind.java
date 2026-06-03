package az.millers.hcm.recruitment.domain;

/** Source / type of a {@link CandidateNote} (M87). Mirrors the V67 CHECK. */
public enum CandidateNoteKind {
    NOTE,
    CALL,
    EMAIL,
    MEETING,
    EVENT,
    REFERRAL,
    OTHER
}
