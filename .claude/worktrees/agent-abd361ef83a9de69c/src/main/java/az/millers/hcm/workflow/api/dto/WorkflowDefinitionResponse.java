package az.millers.hcm.workflow.api.dto;

import java.util.List;
import java.util.UUID;

import az.millers.hcm.workflow.domain.WorkflowDefinition;
import az.millers.hcm.workflow.domain.WorkflowStep;

public record WorkflowDefinitionResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean autoApprove,
        boolean active,
        List<Step> steps) {

    public record Step(int order, String name, String approverRole, Integer slaHours) {
        public static Step from(WorkflowStep s) {
            return new Step(s.getStepOrder(), s.getName(), s.getApproverRole(), s.getSlaHours());
        }
    }

    public static WorkflowDefinitionResponse from(WorkflowDefinition d, List<WorkflowStep> steps) {
        return new WorkflowDefinitionResponse(
                d.getId(), d.getCode(), d.getName(), d.getDescription(),
                d.isAutoApprove(), d.isActive(),
                steps.stream().map(Step::from).toList());
    }
}
