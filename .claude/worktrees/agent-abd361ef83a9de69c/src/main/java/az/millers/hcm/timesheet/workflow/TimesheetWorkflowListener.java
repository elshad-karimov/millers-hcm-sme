package az.millers.hcm.timesheet.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.timesheet.service.TimesheetService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

@Component
public class TimesheetWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(TimesheetWorkflowListener.class);

    private final TimesheetService service;

    public TimesheetWorkflowListener(TimesheetService service) {
        this.service = service;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!TimesheetService.WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!"Timesheet".equals(event.subjectEntity())) return;

        UUID id = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> service.onApproved(id, event.comment());
            case REJECTED, RETURNED      -> service.onRejected(id, event.comment());
            case CANCELLED               -> service.onCancelled(id, event.comment());
            default -> log.warn("Unexpected terminal status {} for timesheet workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
