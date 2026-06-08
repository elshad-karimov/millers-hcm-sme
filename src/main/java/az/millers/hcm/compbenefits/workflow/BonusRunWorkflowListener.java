package az.millers.hcm.compbenefits.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.compbenefits.service.BonusRunService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

/**
 * Translates a completed BONUS_RUN_APPROVAL workflow outcome into the
 * matching state change on the bonus run (M200 / PRD §8.13.12 AC).
 *
 * <p>APPROVED   → {@link BonusRunService#onApproved} — run becomes APPROVED
 *                 and can now be pushed to a payroll run.
 * <p>REJECTED   → {@link BonusRunService#onRejected} — run reverts to
 *                 GENERATED so HR can edit and re-submit.
 * <p>CANCELLED  → treated as rejection; run reverts to GENERATED.
 */
@Component
public class BonusRunWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(BonusRunWorkflowListener.class);

    private final BonusRunService service;

    public BonusRunWorkflowListener(BonusRunService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!BonusRunService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!BonusRunService.WORKFLOW_ENTITY.equals(event.subjectEntity())) return;

        UUID runId = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED ->
                    service.onApproved(runId, event.comment());
            case REJECTED, RETURNED, CANCELLED ->
                    service.onRejected(runId, event.comment());
            default -> log.warn("Unexpected terminal status {} for bonus run workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
