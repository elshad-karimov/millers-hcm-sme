package az.millers.hcm.organization.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.organization.domain.StructureVersion;
import az.millers.hcm.organization.domain.VersionStatus;

public record StructureVersionResponse(
        UUID id,
        int versionNumber,
        LocalDate effectiveDate,
        VersionStatus status,
        String changeReason,
        UUID previousVersionId,
        String createdBy,
        String approvedBy,
        OffsetDateTime activatedAt,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static StructureVersionResponse from(StructureVersion v) {
        return new StructureVersionResponse(
                v.getId(),
                v.getVersionNumber(),
                v.getEffectiveDate(),
                v.getStatus(),
                v.getChangeReason(),
                v.getPreviousVersionId(),
                v.getCreatedBy(),
                v.getApprovedBy(),
                v.getActivatedAt(),
                v.getArchivedAt(),
                v.getCreatedAt(),
                v.getUpdatedAt());
    }
}
