package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.DisciplinaryActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisciplinaryActionRequest(
        @NotNull UUID employeeId,
        @NotNull DisciplinaryActionType actionType,
        @NotNull LocalDate incidentDate,
        @NotNull LocalDate actionDate,
        @Size(max = 8000) String description,
        UUID linkedCaseId) {
}
