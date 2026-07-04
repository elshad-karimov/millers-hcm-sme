package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import az.millers.hcm.corehr.domain.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssetRequest(
        @NotNull AssetType assetType,
        @NotBlank @Size(max = 160) String assetName,
        @Size(max = 120) String assetIdentifier,
        @Size(max = 4000) String description,
        @NotNull LocalDate assignedAt,
        LocalDate expectedReturnDate,
        @Size(max = 40) String conditionAtAssignment,
        @Size(max = 500) String custodyFormUrl,
        @Size(max = 4000) String notes) {
}
