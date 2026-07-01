package az.millers.hcm.compensation.api.dto;

import az.millers.hcm.compensation.domain.ChangeReasonCategory;

/**
 * M360 — Change reason create/update request.
 */
public record ChangeReasonRequest(
    String code,
    String name,
    Boolean affectsWorkflow,
    ChangeReasonCategory category,
    Boolean isActive
) {
}
