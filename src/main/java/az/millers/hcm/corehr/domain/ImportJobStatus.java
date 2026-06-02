package az.millers.hcm.corehr.domain;

/**
 * Lifecycle of an {@link EmployeeImportJob} (M69 / P1-16).
 *
 * <pre>
 *   PENDING    — job row created, file accepted
 *   VALIDATING — parser + per-row validation in progress
 *   PREVIEW    — dry-run finished, validation report available, no rows written
 *   COMMITTED  — non-dry-run finished, valid rows inserted
 *   FAILED     — top-level failure (parser exception, missing required column)
 * </pre>
 */
public enum ImportJobStatus {
    PENDING, VALIDATING, PREVIEW, COMMITTED, FAILED
}
