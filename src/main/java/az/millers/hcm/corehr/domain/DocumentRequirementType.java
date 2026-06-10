package az.millers.hcm.corehr.domain;

/** M262 / PRD §29 — categories of required employee document. */
public enum DocumentRequirementType {
    ID_CARD,
    PASSPORT,
    DIPLOMA,
    NDA,
    BANK_LETTER,
    MEDICAL_CERT,
    BACKGROUND_CHECK,
    DRIVING_LICENSE,
    WORK_PERMIT,
    /** Catch-all for cases not covered by the specific types above. */
    OTHER
}
