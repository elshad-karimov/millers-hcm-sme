package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.AssetType;
import az.millers.hcm.corehr.domain.EmployeeAsset;

public record AssetResponse(
        UUID id,
        UUID employeeId,
        AssetType assetType,
        String assetIdentifier,
        String assetName,
        String description,
        AssetStatus status,
        LocalDate assignedAt,
        LocalDate expectedReturnDate,
        LocalDate returnedAt,
        String conditionAtAssignment,
        String conditionAtReturn,
        String returnAcceptedBy,
        String custodyFormUrl,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static AssetResponse from(EmployeeAsset a) {
        return new AssetResponse(
                a.getId(),
                a.getEmployeeId(),
                a.getAssetType(),
                a.getAssetIdentifier(),
                a.getAssetName(),
                a.getDescription(),
                a.getStatus(),
                a.getAssignedAt(),
                a.getExpectedReturnDate(),
                a.getReturnedAt(),
                a.getConditionAtAssignment(),
                a.getConditionAtReturn(),
                a.getReturnAcceptedBy(),
                a.getCustodyFormUrl(),
                a.getNotes(),
                a.getCreatedAt(),
                a.getCreatedBy(),
                a.getUpdatedAt(),
                a.getUpdatedBy());
    }
}
