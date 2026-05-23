package az.millers.hcm.workflow.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.workflow.domain.ActionType;
import az.millers.hcm.workflow.domain.WorkflowAction;

public record WorkflowActionResponse(
        UUID id,
        UUID instanceId,
        int stepIndex,
        String stepName,
        ActionType action,
        String actor,
        String comment,
        String ipAddress,
        OffsetDateTime createdAt) {

    public static WorkflowActionResponse from(WorkflowAction a) {
        return new WorkflowActionResponse(
                a.getId(),
                a.getInstanceId(),
                a.getStepIndex(),
                a.getStepName(),
                a.getAction(),
                a.getActor(),
                a.getComment(),
                a.getIpAddress(),
                a.getCreatedAt());
    }
}
