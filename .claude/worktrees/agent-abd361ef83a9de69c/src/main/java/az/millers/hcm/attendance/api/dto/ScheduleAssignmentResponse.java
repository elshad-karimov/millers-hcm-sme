package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.ScheduleAssignment;

public record ScheduleAssignmentResponse(
        UUID id,
        UUID employeeId,
        UUID scheduleId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        OffsetDateTime createdAt,
        String createdBy) {

    public static ScheduleAssignmentResponse from(ScheduleAssignment a) {
        return new ScheduleAssignmentResponse(
                a.getId(),
                a.getEmployeeId(),
                a.getScheduleId(),
                a.getEffectiveFrom(),
                a.getEffectiveTo(),
                a.getCreatedAt(),
                a.getCreatedBy());
    }
}
