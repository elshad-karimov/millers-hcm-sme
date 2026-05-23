package az.millers.hcm.payroll.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.payroll.service.PayrollRunService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

@Component
public class PayrollRunWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(PayrollRunWorkflowListener.class);

    private final PayrollRunService service;

    public PayrollRunWorkflowListener(PayrollRunService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!PayrollRunService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!"PayrollRun".equals(event.subjectEntity())) return;
        UUID id = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> service.onApproved(id, event.comment());
            case REJECTED, RETURNED      -> service.onRejected(id, event.comment());
            case CANCELLED               -> service.onCancelled(id, event.comment());
            default -> log.warn("Unexpected terminal status {} for payroll workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
