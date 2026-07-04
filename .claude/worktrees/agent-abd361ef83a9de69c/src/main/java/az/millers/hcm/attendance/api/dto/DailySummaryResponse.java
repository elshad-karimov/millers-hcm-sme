package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;

public record DailySummaryResponse(
        UUID id,
        UUID employeeId,
        LocalDate workDate,
        UUID scheduleId,
        /** M112 — populated when source == ROSTER. */
        UUID shiftId,
        /** M112 — 'SCHEDULE' | 'ROSTER' | 'NONE'. */
        String source,
        LocalTime scheduleStart,
        LocalTime scheduleEnd,
        OffsetDateTime entryTime,
        OffsetDateTime exitTime,
        int rawEventCount,
        int workedMinutes,
        int lateMinutes,
        int earlyMinutes,
        int breakMinutes,
        int overtimeMinutes,
        SummaryStatus status,
        String correctionReason,
        String correctedBy,
        OffsetDateTime correctedAt,
        OffsetDateTime computedAt) {

    public static DailySummaryResponse from(DailySummary s) {
        return new DailySummaryResponse(
                s.getId(),
                s.getEmployeeId(),
                s.getWorkDate(),
                s.getScheduleId(),
                s.getShiftId(),
                s.getSource(),
                s.getScheduleStart(),
                s.getScheduleEnd(),
                s.getEntryTime(),
                s.getExitTime(),
                s.getRawEventCount(),
                s.getWorkedMinutes(),
                s.getLateMinutes(),
                s.getEarlyMinutes(),
                s.getBreakMinutes(),
                s.getOvertimeMinutes(),
                s.getStatus(),
                s.getCorrectionReason(),
                s.getCorrectedBy(),
                s.getCorrectedAt(),
                s.getComputedAt());
    }
}
