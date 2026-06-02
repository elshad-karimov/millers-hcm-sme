package az.millers.hcm.corehr.domain;

/**
 * Configurable employee statuses (PRD 8.1.2). Status transitions are audited.
 *
 * <p>The enum covers both <em>primary</em> statuses (set on
 * {@code employee.employment_status} — exactly one per row) and
 * <em>overlay</em> statuses recorded in
 * {@code core_hr.employee_status_overlay} (M78 / P2-13). Overlay statuses
 * can co-exist on a single employee — e.g. an ACTIVE employee with an
 * MATERNITY_LEAVE overlay and a separate BUSINESS_TRIP overlay closed last
 * week.
 *
 * <p>Primary-status-only values: {@code ACTIVE, ON_PROBATION, TERMINATED,
 * RETIRED, CONTRACTOR, INTERN}.
 *
 * <p>Overlay-friendly values: {@code ON_LEAVE, ON_BUSINESS_TRIP, SUSPENDED,
 * MATERNITY_LEAVE, MILITARY_SERVICE, EDUCATIONAL_LEAVE, GARDEN_LEAVE,
 * NON_ACTIVE}. These can also be used as the primary status for back-compat
 * with pre-M78 flows that mutated employment_status directly on leave start.
 */
public enum EmploymentStatus {
    ACTIVE,
    ON_PROBATION,
    ON_LEAVE,
    ON_BUSINESS_TRIP,
    SUSPENDED,
    TERMINATED,
    RETIRED,
    CONTRACTOR,
    INTERN,
    /** M78 / P2-14 — paid/unpaid maternity. */
    MATERNITY_LEAVE,
    /** M78 / P2-14 — compulsory military service. */
    MILITARY_SERVICE,
    /** M78 / P2-14 — long-form education sabbatical. */
    EDUCATIONAL_LEAVE,
    /** M78 / P2-14 — employee removed from duties pre-termination. */
    GARDEN_LEAVE,
    /** M78 / P2-14 — generic "paused" — payroll skipped but not terminated. */
    NON_ACTIVE
}
