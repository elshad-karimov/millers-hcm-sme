package az.millers.hcm.organization.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record RollbackRequest(
        @NotNull UUID sourceVersionId,
        @NotNull LocalDate effectiveDate,
        String reason) {
}
