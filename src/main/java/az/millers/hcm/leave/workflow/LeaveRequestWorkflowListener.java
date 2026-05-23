package az.millers.hcm.leave.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.leave.service.LeaveRequestService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

/**
 * Translates a completed {@code LEAVE_REQUEST_APPROVAL} workflow into the
 * matching state change on the leave request, and updates the balance.
 */
@Component
public class LeaveRequestWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestWorkflowListener.class);

    private final LeaveRequestService requestService;

    public LeaveRequestWorkflowListener(LeaveRequestService requestService) {
        this.requestService = requestService;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!LeaveRequestService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!"LeaveRequest".equals(event.subjectEntity())) return;

        UUID requestId = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> requestService.onApproved(requestId, event.comment());
            case REJECTED, RETURNED      -> requestService.onRejected(requestId, event.comment());
            case CANCELLED               -> requestService.onCancelled(requestId, event.comment());
            default -> log.warn("Unexpected terminal status {} for leave workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
