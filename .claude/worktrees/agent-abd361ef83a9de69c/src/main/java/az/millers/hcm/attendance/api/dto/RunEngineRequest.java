package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record RunEngineRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        UUID employeeId) {
}
