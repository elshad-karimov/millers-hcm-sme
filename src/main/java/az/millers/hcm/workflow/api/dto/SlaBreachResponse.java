package az.millers.hcm.workflow.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.workflow.domain.EscalationAction;
import az.millers.hcm.workflow.domain.SlaBreach;

/**
 * M126 — wire shape of a recorded SLA breach. Carries denormalised
 * subject info so the HR dashboard can render rows without joining
 * back to the originating module.
 */
public record SlaBreachResponse(
        UUID id,
        UUID instanceId,
        int stepIndex,
        OffsetDateTime breachedAt,
        BigDecimal hoursOverdue,
        EscalationAction actionTaken,
        String notifiedTarget,
        /** Denormalised from workflow_instance for the SPA. */
        String definitionCode,
        String subjectModule,
        String subjectEntity,
        String subjectId,
        String title,
        String currentStepRole) {

    public static SlaBreachResponse from(SlaBreach b) {
        return new SlaBreachResponse(
                b.getId(), b.getInstanceId(), b.getStepIndex(),
                b.getBreachedAt(), b.getHoursOverdue(),
                b.getActionTaken(), b.getNotifiedTarget(),
                null, null, null, null, null, null);
    }
}
