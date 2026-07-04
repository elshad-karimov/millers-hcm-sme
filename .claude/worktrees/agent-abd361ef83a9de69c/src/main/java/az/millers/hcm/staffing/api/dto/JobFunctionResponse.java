package az.millers.hcm.staffing.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.JobFunction;

public record JobFunctionResponse(
        UUID id, String code, String name, String description,
        UUID jobFamilyId, boolean active,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static JobFunctionResponse from(JobFunction f) {
        return new JobFunctionResponse(
                f.getId(), f.getCode(), f.getName(), f.getDescription(),
                f.getJobFamilyId(), f.isActive(),
                f.getCreatedAt(), f.getCreatedBy(),
                f.getUpdatedAt(), f.getUpdatedBy());
    }
}
