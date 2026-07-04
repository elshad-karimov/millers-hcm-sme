package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ScheduleAssignmentRequest(
        @NotNull UUID employeeId,
        @NotNull UUID scheduleId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
