package az.millers.hcm.leave.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AbsenceDismissRequest(
        @NotNull UUID employeeId,
        @NotEmpty List<LocalDate> absenceDates,
        String notes
) {}
