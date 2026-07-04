package az.millers.hcm.workflow.api.dto;

import java.util.List;
import java.util.UUID;

import az.millers.hcm.workflow.domain.ActionType;
import jakarta.validation.constraints.NotNull;

/**
 * M435 — Bulk action request DTO.
 */
public record BulkActionRequest(
        @NotNull List<UUID> instanceIds,
        @NotNull ActionType action,
        String comment
) {
}
