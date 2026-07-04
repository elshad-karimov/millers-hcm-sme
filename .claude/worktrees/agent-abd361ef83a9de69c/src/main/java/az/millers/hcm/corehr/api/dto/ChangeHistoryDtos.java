package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the M117 per-employee change-history viewer.
 *
 * <p>Distinct from the M76 lifecycle timeline (HIRE / STATUS_CHANGE /
 * LEAVE_REQUEST / etc.) — this one focuses on <em>field changes</em>:
 * employment-history slices from M62, plus the JSON-diff of audit-log
 * rows for the Employee entity.
 */
public final class ChangeHistoryDtos {

    private ChangeHistoryDtos() {}

    /**
     * Stable categories the timeline collapses different sources into.
     *
     * <ul>
     *   <li>{@link #EMPLOYMENT_CHANGE} — a slice in
     *       {@code employee_employment_history} (position, department,
     *       manager, FTE, employment type).</li>
     *   <li>{@link #STATUS_CHANGE} — a slice in
     *       {@code employee_status_history}.</li>
     *   <li>{@link #AUDIT} — a row in {@code audit.audit_log} where
     *       {@code entity_name = 'Employee'}.</li>
     * </ul>
     */
    public enum EventCategory {
        EMPLOYMENT_CHANGE,
        STATUS_CHANGE,
        AUDIT
    }

    /**
     * One unified change event. Sorted by {@link #eventTime} descending in
     * the response so the most recent change is at the top.
     *
     * <p>{@code effectiveDate} (the day the change took effect) and
     * {@code eventTime} (when the row was written) differ for back-dated
     * changes; the UI shows both.
     */
    public record ChangeEvent(
            EventCategory category,
            /** When the row was written — primary sort key. */
            OffsetDateTime eventTime,
            /** When the change took effect (history slices only — null for audit). */
            LocalDate effectiveDate,
            /** Short label — e.g. "POSITION_CHANGE", "STATUS → ACTIVE", "UPDATE". */
            String action,
            /** Human-readable one-line summary. */
            String title,
            /** Multi-line body — pre-rendered field-level deltas where possible. */
            String summary,
            /** {@code changed_by} / {@code actor}. */
            String actor,
            /** Which service wrote the row (for traceability). */
            String sourceModule,
            String sourceEntity,
            String sourceId,
            /** Audit only — old JSON. */
            String oldValue,
            /** Audit only — new JSON. */
            String newValue,
            /** Stable identifier — UUID for history slices, audit-log id for audit. */
            UUID rowId) {
    }

    public record EmployeeChangeHistory(
            UUID employeeId,
            String employeeName,
            int eventCount,
            List<ChangeEvent> events) {
    }
}
