package az.millers.hcm.attendance.api.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.ScheduleType;
import az.millers.hcm.attendance.domain.WorkSchedule;

public record WorkScheduleResponse(
        UUID id,
        String code,
        String name,
        ScheduleType scheduleType,
        LocalTime workStart,
        LocalTime workEnd,
        int breakMinutes,
        int gracePeriodMinutes,
        String workDays,
        Integer overtimeThresholdMinutes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static WorkScheduleResponse from(WorkSchedule s) {
        return new WorkScheduleResponse(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getScheduleType(),
                s.getWorkStart(),
                s.getWorkEnd(),
                s.getBreakMinutes(),
                s.getGracePeriodMinutes(),
                s.getWorkDays(),
                s.getOvertimeThresholdMinutes(),
                s.isActive(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
