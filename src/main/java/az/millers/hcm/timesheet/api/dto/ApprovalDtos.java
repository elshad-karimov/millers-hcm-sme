package az.millers.hcm.timesheet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wire types for manager approval and HR period control.
 *
 * <p>Quantities and hours only. A manager judges whether the recorded time is
 * true; what it pays is not shown here and is not fetched.
 */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    /** One row in the manager's approval queue. */
    public record QueueRow(
            UUID timesheetId,
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String positionTitle,
            int year,
            int month,
            String status,
            BigDecimal totalHours,
            BigDecimal overtimeHours,
            int daysEntered,
            int daysReturned,
            int warnings,
            int blockingIssues,
            boolean cleanForBulkApproval,
            OffsetDateTime submittedAt) {
    }

    /** One day on the manager's review screen. */
    public record ReviewDay(
            UUID dayId,
            LocalDate date,
            String dayOfWeek,
            String workType,
            String entrySource,
            String approvalState,
            String returnReason,
            boolean holiday,
            BigDecimal enteredHours,
            BigDecimal attendanceHours,
            BigDecimal varianceHours,
            String varianceExplanation,
            String employeeNote,
            /** Where the day was worked — drives the offshore/quayside rate. */
            String workLocation,
            /** Project the day is booked to, resolved to its name. */
            String project,
            List<DailyEntryDtos.QuantityView> quantities,
            List<DailyEntryDtos.FindingView> findings) {
    }

    /** Everything a manager needs to judge one month. */
    public record ReviewView(
            UUID timesheetId,
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String positionTitle,
            int year,
            int month,
            String status,
            boolean actionable,
            String notActionableReason,
            OffsetDateTime submittedAt,
            String employeeComment,
            BigDecimal totalEnteredHours,
            BigDecimal totalAttendanceHours,
            BigDecimal totalVarianceHours,
            Map<String, BigDecimal> totals,
            List<ReviewDay> days,
            List<DailyEntryDtos.FindingView> findings,
            List<CorrectionView> corrections) {
    }

    /** Manager decision payloads. */
    public record ApproveRequest(String comment) {
    }

    /** Return names the days to fix — everything else stays approved. */
    public record ReturnRequest(List<LocalDate> dates, String reason) {
    }

    public record RejectRequest(String reason) {
    }

    public record BulkApproveRequest(List<UUID> timesheetIds, String comment) {
    }

    /** Outcome per timesheet, so a partial bulk result is never silent. */
    public record BulkApproveResult(
            List<UUID> approved,
            Map<String, String> skipped) {
    }

    // ---- HR period control ----

    public record ControlRow(
            UUID timesheetId,
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String status,
            BigDecimal totalHours,
            int warnings,
            int blockingIssues,
            String exception,
            boolean payrollReady) {
    }

    public record ControlBoard(
            int year,
            int month,
            String periodStatus,
            OffsetDateTime lockedAt,
            String lockedBy,
            int employees,
            int draft,
            int submitted,
            int returned,
            int approved,
            int locked,
            int payrollReady,
            boolean lockable,
            String lockBlockedReason,
            List<ControlRow> rows) {
    }

    public record LockRequest(String reason) {
    }

    // ---- corrections ----

    public record CorrectionRequestInput(
            LocalDate date,
            String requestedValue,
            String reason) {
    }

    public record CorrectionDecision(boolean approve, String note) {
    }

    public record CorrectionView(
            UUID id,
            UUID timesheetId,
            UUID employeeId,
            String employeeName,
            LocalDate workDate,
            String currentValue,
            String requestedValue,
            String reason,
            String status,
            String requestedBy,
            OffsetDateTime requestedAt,
            String decidedBy,
            OffsetDateTime decidedAt,
            String decisionNote) {
    }
}
