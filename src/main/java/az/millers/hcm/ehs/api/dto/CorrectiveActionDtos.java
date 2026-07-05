package az.millers.hcm.ehs.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.ehs.domain.CorrectiveAction;
import az.millers.hcm.ehs.domain.CorrectiveActionPriority;
import az.millers.hcm.ehs.domain.CorrectiveActionStatus;

public class CorrectiveActionDtos {

    public record CreateCorrectiveActionRequest(
            UUID incidentId,
            UUID inspectionId,
            UUID riskAssessmentId,
            String description,
            String responsibleUsername,
            LocalDate dueDate,
            CorrectiveActionPriority priority
    ) {}

    public record UpdateCorrectiveActionStatusRequest(
            CorrectiveActionStatus status,
            UUID evidenceAttachmentId
    ) {}

    public record CorrectiveActionResponse(
            UUID id,
            String tenantId,
            UUID incidentId,
            UUID inspectionId,
            UUID riskAssessmentId,
            String description,
            String responsibleUsername,
            LocalDate dueDate,
            CorrectiveActionPriority priority,
            CorrectiveActionStatus status,
            UUID evidenceAttachmentId,
            OffsetDateTime closedAt,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        public static CorrectiveActionResponse from(CorrectiveAction action) {
            return new CorrectiveActionResponse(
                    action.getId(),
                    action.getTenantId(),
                    action.getIncidentId(),
                    action.getInspectionId(),
                    action.getRiskAssessmentId(),
                    action.getDescription(),
                    action.getResponsibleUsername(),
                    action.getDueDate(),
                    action.getPriority(),
                    action.getStatus(),
                    action.getEvidenceAttachmentId(),
                    action.getClosedAt(),
                    action.getCreatedBy(),
                    action.getCreatedAt(),
                    action.getUpdatedBy(),
                    action.getUpdatedAt()
            );
        }
    }
}
