package az.millers.hcm.corehr.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.service.PersonalInfoChangeService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

/**
 * Translates a completed PERSONAL_INFO_CHANGE_APPROVAL workflow into the
 * matching state change (M79 / P2-25/26). Mirrors the M77 letter and
 * M67 disciplinary listeners.
 */
@Component
public class PersonalInfoChangeWorkflowListener {

    private static final Logger log =
            LoggerFactory.getLogger(PersonalInfoChangeWorkflowListener.class);

    private final PersonalInfoChangeService service;

    public PersonalInfoChangeWorkflowListener(PersonalInfoChangeService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!PersonalInfoChangeService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!PersonalInfoChangeService.WORKFLOW_ENTITY.equals(event.subjectEntity())) return;

        UUID id = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED ->
                    service.onApproved(id, event.actor(), event.comment());
            case REJECTED, RETURNED ->
                    service.onRejected(id, event.actor(), event.comment());
            case CANCELLED ->
                    service.onCancelled(id, event.actor(), event.comment());
            default -> log.warn(
                    "Unexpected terminal status {} for personal-info workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
