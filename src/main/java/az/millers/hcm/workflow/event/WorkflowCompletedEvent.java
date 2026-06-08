package az.millers.hcm.workflow.event;

import java.util.UUID;

import az.millers.hcm.workflow.domain.WorkflowStatus;

/**
 * Published when a {@code WorkflowInstance} reaches a terminal state.
 * Modules that initiated the workflow listen and react.
 *
 * @param actor       Keycloak username of the approver/rejector
 * @param initiatedBy Keycloak username of the original submitter — used by the
 *                    generic {@code WorkflowOutcomeNotificationListener} to notify
 *                    the requestor of the decision (M188 / PRD WORKFLOW_OUTCOME)
 */
public record WorkflowCompletedEvent(
        UUID instanceId,
        String definitionCode,
        String subjectModule,
        String subjectEntity,
        String subjectId,
        WorkflowStatus status,
        String comment,
        String actor,
        String initiatedBy) {
}
