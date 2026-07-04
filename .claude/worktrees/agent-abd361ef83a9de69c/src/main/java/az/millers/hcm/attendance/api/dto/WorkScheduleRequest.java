package az.millers.hcm.attendance.api.dto;

import java.time.LocalTime;

import az.millers.hcm.attendance.domain.ScheduleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkScheduleRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull ScheduleType scheduleType,
        @NotNull LocalTime workStart,
        @NotNull LocalTime workEnd,
        @Min(0) Integer breakMinutes,
        @Min(0) Integer gracePeriodMinutes,
        @NotNull @Pattern(regexp = "^[01]{7}$", message = "workDays must be 7 characters of 0/1 (Mon..Sun)")
        String workDays,
        Integer overtimeThresholdMinutes,
        Boolean active) {
}
