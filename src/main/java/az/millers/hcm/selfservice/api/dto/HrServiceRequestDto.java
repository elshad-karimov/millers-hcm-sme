package az.millers.hcm.selfservice.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.selfservice.domain.HrServiceRequest;
import az.millers.hcm.selfservice.domain.ServiceRequestCategory;
import az.millers.hcm.selfservice.domain.ServiceRequestPriority;
import az.millers.hcm.selfservice.domain.ServiceRequestStatus;

public record HrServiceRequestDto(
        UUID id,
        String requestNo,
        UUID employeeId,
        ServiceRequestCategory category,
        ServiceRequestPriority priority,
        String subject,
        String description,
        ServiceRequestStatus status,
        String assignedToUsername,
        OffsetDateTime slaDue,
        OffsetDateTime resolvedAt,
        String resolutionNotes,
        OffsetDateTime createdAt,
        String createdBy
) {
    public static HrServiceRequestDto from(HrServiceRequest entity) {
        return new HrServiceRequestDto(
                entity.getId(),
                entity.getRequestNo(),
                entity.getEmployeeId(),
                entity.getCategory(),
                entity.getPriority(),
                entity.getSubject(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getAssignedToUsername(),
                entity.getSlaDue(),
                entity.getResolvedAt(),
                entity.getResolutionNotes(),
                entity.getCreatedAt(),
                entity.getCreatedBy()
        );
    }
}
