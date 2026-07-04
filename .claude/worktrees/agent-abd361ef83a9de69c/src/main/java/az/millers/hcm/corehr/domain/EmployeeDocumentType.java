package az.millers.hcm.corehr.domain;

/**
 * Categorises a document stored against an employee record (M169 / PRD §8.1.3).
 */
public enum EmployeeDocumentType {
    EMPLOYMENT_CONTRACT,
    ID_COPY,
    PASSPORT_COPY,
    CERTIFICATE,
    EDUCATION,
    MEDICAL,
    ORDER,
    SALARY_CHANGE_DOC,
    TERMINATION_DOC,
    OTHER
}
