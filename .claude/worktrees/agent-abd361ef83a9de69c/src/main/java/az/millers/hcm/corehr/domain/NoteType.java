package az.millers.hcm.corehr.domain;

/**
 * Classification tag for {@link EmployeeNote} rows (M72 / P2-10). Drives
 * default {@link NoteVisibility} selection — a NoteType.PAYROLL note defaults
 * to HR_ONLY visibility, a NoteType.MANAGER note to MANAGER_ONLY, etc.
 */
public enum NoteType {
    GENERAL,
    CONFIDENTIAL,
    MANAGER,
    HR,
    PERFORMANCE,
    PAYROLL,
    SYSTEM
}
