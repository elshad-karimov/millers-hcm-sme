package az.millers.hcm.workflow.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;

public record WorkflowInstanceResponse(
        UUID id,
        UUID definitionId,
        String definitionCode,
        String subjectModule,
        String subjectEntity,
        String subjectId,
        String title,
        int currentStepIndex,
        String currentStepRole,
        WorkflowStatus status,
        String initiatedBy,
        OffsetDateTime initiatedAt,
        OffsetDateTime completedAt,
        String payload) {

    public static WorkflowInstanceResponse from(WorkflowInstance i) {
        return new WorkflowInstanceResponse(
                i.getId(),
                i.getDefinitionId(),
                i.getDefinitionCode(),
                i.getSubjectModule(),
                i.getSubjectEntity(),
                i.getSubjectId(),
                i.getTitle(),
                i.getCurrentStepIndex(),
                i.getCurrentStepRole(),
                i.getStatus(),
                i.getInitiatedBy(),
                i.getInitiatedAt(),
                i.getCompletedAt(),
                i.getPayload());
    }
}
