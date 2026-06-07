package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeaveRequestStatus;

/**
 * M131 — wire DTOs for the team time-off calendar.
 */
public final class TeamCalendarDtos {

    private TeamCalendarDtos() {}

    /** One leave row on the calendar. */
    public record TeamLeaveEntry(
            UUID requestId,
            UUID employeeId,
            String employeeNo,
            String employeeName,
            UUID leaveTypeId,
            String leaveTypeName,
            String leaveTypeColor,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalDays,
            boolean halfDay,
            LeaveRequestStatus status) {}

    /**
     * One day's roll-up: how many of the team are out + the % of team
     * size + whether that day exceeded the configured threshold. Lets
     * the SPA paint the day cell without recomputing.
     */
    public record DailyRollup(
            LocalDate date,
            int outCount,
            BigDecimal percentOff,
            boolean flagged) {}

    public record TeamCalendarResponse(
            LocalDate windowStart,
            LocalDate windowEnd,
            UUID orgUnitId,
            int teamSize,
            BigDecimal thresholdPercent,
            List<TeamLeaveEntry> entries,
            List<DailyRollup> days) {}
}
