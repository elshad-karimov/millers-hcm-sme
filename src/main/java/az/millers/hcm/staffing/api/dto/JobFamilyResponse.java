package az.millers.hcm.staffing.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.JobFamily;

public record JobFamilyResponse(
        UUID id, String code, String name, String description, boolean active,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static JobFamilyResponse from(JobFamily f) {
        return new JobFamilyResponse(
                f.getId(), f.getCode(), f.getName(), f.getDescription(), f.isActive(),
                f.getCreatedAt(), f.getCreatedBy(),
                f.getUpdatedAt(), f.getUpdatedBy());
    }
}
