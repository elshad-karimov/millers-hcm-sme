package az.millers.hcm.performance.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.performance.service.PerformanceReviewService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

@Component
public class PerformanceWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(PerformanceWorkflowListener.class);

    private final PerformanceReviewService service;

    public PerformanceWorkflowListener(PerformanceReviewService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!PerformanceReviewService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!"PerformanceReview".equals(event.subjectEntity())) return;
        UUID id = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> service.onApproved(id, event.comment());
            case REJECTED, RETURNED      -> service.onRejected(id, event.comment());
            case CANCELLED               -> service.onCancelled(id, event.comment());
            default -> log.warn("Unexpected terminal status {} for performance review workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
