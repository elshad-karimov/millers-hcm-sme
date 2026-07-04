package az.millers.hcm.timesheet.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerateTimesheetRequest(
        @NotNull UUID employeeId,
        @NotNull @Min(2000) Integer periodYear,
        @NotNull @Min(1) @Max(12) Integer periodMonth) {
}
