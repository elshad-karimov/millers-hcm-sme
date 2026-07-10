package az.millers.hcm.contingent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ConvertToFTERequest(
    @NotBlank String newEmploymentType,
    @NotNull LocalDate effectiveDate
) {}
