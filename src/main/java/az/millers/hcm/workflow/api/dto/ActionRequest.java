package az.millers.hcm.workflow.api.dto;

import az.millers.hcm.workflow.domain.ActionType;
import jakarta.validation.constraints.NotNull;

public record ActionRequest(
        @NotNull ActionType action,
        String comment,
        /** M162 — required when action == DELEGATE; username of the recipient. */
        String delegateTo) {
}
