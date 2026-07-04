package az.millers.hcm.organization.workflow;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import az.millers.hcm.organization.service.OrgStructureService;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

/**
 * Translates a completed {@code ORG_STRUCTURE_APPROVAL} workflow into the
 * corresponding state transition on the version.
 */
@Component
public class OrgStructureWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(OrgStructureWorkflowListener.class);

    public static final String DEFINITION_CODE = "ORG_STRUCTURE_APPROVAL";

    private final OrgStructureService orgService;

    public OrgStructureWorkflowListener(OrgStructureService orgService) {
        this.orgService = orgService;
    }

    @EventListener
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!DEFINITION_CODE.equals(event.definitionCode())) return;
        if (!"StructureVersion".equals(event.subjectEntity())) return;

        UUID versionId = UUID.fromString(event.subjectId());
        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> orgService.markApproved(versionId, event.comment());
            case REJECTED -> orgService.markRejected(versionId, event.comment());
            case CANCELLED, RETURNED -> orgService.returnToDraft(versionId, event.comment());
            default -> log.warn("Unexpected terminal status {} for org workflow {}",
                    event.status(), event.instanceId());
        }
    }
}
