package az.millers.hcm.letters.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.letters.service.LetterRequestService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

/**
 * Translates a completed LETTER_REQUEST_APPROVAL workflow into the matching
 * state change on the letter request (M77 / P2-17). Mirrors the shape of
 * the existing leave / disciplinary listeners — no new wiring needed.
 */
@Component
public class LetterRequestWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(LetterRequestWorkflowListener.class);

    private final LetterRequestService service;

    public LetterRequestWorkflowListener(LetterRequestService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!LetterRequestService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!LetterRequestService.WORKFLOW_ENTITY.equals(event.subjectEntity())) return;

        UUID id = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED ->
                    service.onApproved(id, event.actor(), event.comment());
            case REJECTED, RETURNED ->
                    service.onRejected(id, event.actor(), event.comment());
            case CANCELLED ->
                    service.onCancelled(id, event.actor(), event.comment());
            default -> log.warn("Unexpected terminal status {} for letter workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
