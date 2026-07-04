package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import az.millers.hcm.corehr.domain.AssetStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for closing out an asset assignment — supports the regular
 * RETURNED path plus LOST / DAMAGED / WRITTEN_OFF terminal states.
 */
public record AssetReturnRequest(
        @NotNull AssetStatus status,
        @NotNull LocalDate returnedAt,
        @Size(max = 40) String conditionAtReturn,
        @Size(max = 4000) String notes) {
}
