package az.millers.hcm.corehr.domain;

/** M262 / PRD §29 — Status of a required-document obligation. */
public enum RequiredDocumentStatus {
    /** Operator/employee still owes the document. */
    PENDING,
    /** The employee has uploaded an attachment that HR accepted. */
    SATISFIED,
    /** HR explicitly waived the requirement (e.g. internal transfer). */
    WAIVED,
    /** The required_by_date has passed without satisfaction. */
    EXPIRED;

    public boolean isTerminal() {
        return this == SATISFIED || this == WAIVED || this == EXPIRED;
    }
}
