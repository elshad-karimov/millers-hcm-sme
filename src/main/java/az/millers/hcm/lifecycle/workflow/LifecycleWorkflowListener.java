package az.millers.hcm.lifecycle.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.lifecycle.service.ContractChangeService;
import az.millers.hcm.lifecycle.service.DisciplinaryActionService;
import az.millers.hcm.lifecycle.service.TerminationService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

@Component
public class LifecycleWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(LifecycleWorkflowListener.class);

    private final TerminationService termination;
    private final ContractChangeService contractChange;
    private final DisciplinaryActionService disciplinary;

    public LifecycleWorkflowListener(TerminationService termination,
                                      ContractChangeService contractChange,
                                      DisciplinaryActionService disciplinary) {
        this.termination = termination;
        this.contractChange = contractChange;
        this.disciplinary = disciplinary;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (TerminationService.WORKFLOW_DEFINITION.equals(event.definitionCode())
                && "TerminationRequest".equals(event.subjectEntity())) {
            UUID id = UUID.fromString(event.subjectId());
            switch (event.status()) {
                case APPROVED, AUTO_APPROVED -> termination.onApproved(id, event.comment());
                case REJECTED, RETURNED      -> termination.onRejected(id, event.comment());
                case CANCELLED               -> termination.onCancelled(id, event.comment());
                default -> log.warn("Unexpected terminal status {} for termination workflow {}",
                        event.status(), event.instanceId());
            }
            return;
        }
        if (ContractChangeService.WORKFLOW_DEFINITION.equals(event.definitionCode())
                && "ContractChange".equals(event.subjectEntity())) {
            UUID id = UUID.fromString(event.subjectId());
            switch (event.status()) {
                case APPROVED, AUTO_APPROVED -> contractChange.onApproved(id, event.comment());
                case REJECTED, RETURNED      -> contractChange.onRejected(id, event.comment());
                case CANCELLED               -> contractChange.onCancelled(id, event.comment());
                default -> log.warn("Unexpected terminal status {} for contract-change workflow {}",
                        event.status(), event.instanceId());
            }
            return;
        }
        // M67 / P1-12 — disciplinary action approval. Same dispatch shape; the
        // service decides what to do with each terminal status.
        if (DisciplinaryActionService.WORKFLOW_DEFINITION.equals(event.definitionCode())
                && "DisciplinaryAction".equals(event.subjectEntity())) {
            UUID id = UUID.fromString(event.subjectId());
            switch (event.status()) {
                case APPROVED, AUTO_APPROVED -> disciplinary.onApproved(id, event.comment());
                case REJECTED, RETURNED      -> disciplinary.onRejected(id, event.comment());
                case CANCELLED               -> disciplinary.onCancelled(id, event.comment());
                default -> log.warn("Unexpected terminal status {} for disciplinary workflow {}",
                        event.status(), event.instanceId());
            }
        }
    }
}
