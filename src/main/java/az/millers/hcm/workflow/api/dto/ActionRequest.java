package az.millers.hcm.workflow.api.dto;

import az.millers.hcm.workflow.domain.ActionType;
import jakarta.validation.constraints.NotNull;

public record ActionRequest(
        @NotNull ActionType action,
        String comment) {
}
