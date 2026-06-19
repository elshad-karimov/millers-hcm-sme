package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ResignationSubmitRequest(
        @NotNull UUID employeeId,
        @NotNull LocalDate resignationDate,
        @NotNull LocalDate proposedLastWorkingDate,
        String reasonText,
        String comments
) {}
