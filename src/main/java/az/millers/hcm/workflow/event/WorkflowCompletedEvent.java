package az.millers.hcm.workflow.event;

import java.util.UUID;

import az.millers.hcm.workflow.domain.WorkflowStatus;

/**
 * Published when a {@code WorkflowInstance} reaches a terminal state.
 * Modules that initiated the workflow listen and react.
 */
public record WorkflowCompletedEvent(
        UUID instanceId,
        String definitionCode,
        String subjectModule,
        String subjectEntity,
        String subjectId,
        WorkflowStatus status,
        String comment,
        String actor) {
}
