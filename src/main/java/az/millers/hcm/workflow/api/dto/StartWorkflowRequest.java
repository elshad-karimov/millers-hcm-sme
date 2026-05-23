package az.millers.hcm.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record StartWorkflowRequest(
        @NotBlank String definitionCode,
        @NotBlank String subjectModule,
        @NotBlank String subjectEntity,
        @NotBlank String subjectId,
        @NotBlank String title,
        Object payload) {
}
