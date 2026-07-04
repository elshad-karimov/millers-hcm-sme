package az.millers.hcm.organization.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record StructureVersionRequest(
        @NotNull LocalDate effectiveDate,
        String changeReason) {
}
