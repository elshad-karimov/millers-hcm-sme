package az.millers.hcm.businesstrip.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.businesstrip.service.BusinessTripService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

@Component
public class BusinessTripWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(BusinessTripWorkflowListener.class);

    private final BusinessTripService service;

    public BusinessTripWorkflowListener(BusinessTripService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!BusinessTripService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!"BusinessTripRequest".equals(event.subjectEntity())) return;
        UUID tripId = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> service.onApproved(tripId, event.comment());
            case REJECTED, RETURNED      -> service.onRejected(tripId, event.comment());
            case CANCELLED               -> service.onCancelled(tripId, event.comment());
            default -> log.warn("Unexpected terminal status {} for BT workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
