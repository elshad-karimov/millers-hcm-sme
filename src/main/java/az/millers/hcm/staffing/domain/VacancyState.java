package az.millers.hcm.staffing.domain;

/** Vacancy state of a position (PRD 8.3.2). */
public enum VacancyState {
    OCCUPIED,
    VACANT,
    PARTIALLY_OCCUPIED,
    FROZEN,
    PLANNED,
    CANCELLED
}
