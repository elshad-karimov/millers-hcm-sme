package az.millers.hcm.permission.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.permission.service.PermissionRequestService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

@Component
public class PermissionRequestWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionRequestWorkflowListener.class);

    private final PermissionRequestService service;

    public PermissionRequestWorkflowListener(PermissionRequestService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!PermissionRequestService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!"PermissionRequest".equals(event.subjectEntity())) return;
        UUID id = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> service.onApproved(id, event.comment());
            case REJECTED, RETURNED      -> service.onRejected(id, event.comment());
            case CANCELLED               -> service.onCancelled(id, event.comment());
            default -> log.warn("Unexpected terminal status {} for permission workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
