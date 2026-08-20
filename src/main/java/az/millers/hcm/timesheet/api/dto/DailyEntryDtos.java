package az.millers.hcm.timesheet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wire types for employee daily timesheet capture.
 *
 * <p>Quantities only. Nothing here carries a rate, an amount or any other
 * monetary value — that is payroll's surface, not the employee's.
 */
public final class DailyEntryDtos {

    private DailyEntryDtos() {
    }

    /** A category the employee may enter, for the work type they picked. */
    public record CategoryOption(
            String code,
            String name,
            String unit,
            boolean derived,
            String source,
            BigDecimal maxPerDay,
            int displayOrder,
            List<String> appliesTo) {
    }

    /** One quantity on a day, as sent by the client. */
    public record QuantityInput(
            String categoryCode,
            BigDecimal quantity,
            String overrideReason) {
    }

    /**
     * What the employee submits for one day.
     *
     * @param workLocation V322 — where it was worked; free text, or one of
     *                     {@code MonthView.workLocations}
     * @param projectId    V322 — cost attribution; may be null unless the tenant
     *                     turns on {@code timesheet.validation.require-project}
     * @param taskCode     V322 — free-text cost code, an alternative to projectId
     */
    public record DayEntryRequest(
            String workType,
            List<QuantityInput> quantities,
            String note,
            String varianceExplanation,
            String workLocation,
            UUID projectId,
            String taskCode) {

        /** Pre-V322 callers (drawer, copy-previous) that carry no location or project. */
        public DayEntryRequest(String workType, List<QuantityInput> quantities,
                               String note, String varianceExplanation) {
            this(workType, quantities, note, varianceExplanation, null, null, null);
        }
    }

    /**
     * One day inside a bulk save.
     *
     * @param date  the day being written
     * @param entry what to record; {@code null} clears the day back to "not entered"
     */
    public record BulkDayEntry(LocalDate date, DayEntryRequest entry) {
    }

    /** A grid's worth of edits, applied atomically. */
    public record BulkDayEntryRequest(List<BulkDayEntry> days) {
    }

    /** One quantity as returned, including whether the system derived it. */
    public record QuantityView(
            String categoryCode,
            String categoryName,
            String unit,
            BigDecimal quantity,
            boolean derived,
            String derivedFrom) {
    }

    /** A finding surfaced against a day or the month. */
    public record FindingView(
            String code,
            String severity,
            LocalDate date,
            String message) {
    }

    /** One day as the employee sees it. */
    public record DayView(
            UUID id,
            LocalDate date,
            String dayOfWeek,
            String workType,
            String entrySource,
            boolean holiday,
            boolean scheduledWorkingDay,
            boolean readOnly,
            String readOnlyReason,
            UUID leaveRequestId,
            BigDecimal attendanceHours,
            BigDecimal attendanceVarianceHours,
            String varianceExplanation,
            String note,
            String workLocation,
            UUID projectId,
            String taskCode,
            List<QuantityView> quantities,
            List<FindingView> findings) {
    }

    /** The whole month: header, days, category totals and validation state. */
    public record MonthView(
            UUID timesheetId,
            int year,
            int month,
            String status,
            boolean editable,
            OffsetDateTime submittedAt,
            String submittedBy,
            String employeeComment,
            List<DayView> days,
            Map<String, BigDecimal> totals,
            List<CategoryOption> categories,
            List<FindingView> findings,
            boolean submittable,
            /** V322: permitted work locations; empty = free text. */
            List<String> workLocations,
            /** V322: selectable projects for the Cost Code column. */
            List<ProjectOption> projects,
            /** V322: minutes the payable overtime figure is rounded to; 0 = none. */
            int overtimeRoundingMinutes) {
    }

    /** A project the employee may charge a day to. */
    public record ProjectOption(UUID id, String code, String name) {
    }

    /** Employee confirmation at submission time. */
    public record SubmitRequest(
            boolean confirmed,
            String comment) {
    }
}
