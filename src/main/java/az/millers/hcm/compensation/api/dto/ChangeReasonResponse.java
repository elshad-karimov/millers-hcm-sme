package az.millers.hcm.compensation.api.dto;

import java.util.UUID;

import az.millers.hcm.compensation.domain.ChangeReason;
import az.millers.hcm.compensation.domain.ChangeReasonCategory;

/**
 * M360 — Change reason response DTO.
 */
public record ChangeReasonResponse(
    UUID id,
    String code,
    String name,
    Boolean affectsWorkflow,
    ChangeReasonCategory category,
    Boolean isActive
) {
    public static ChangeReasonResponse from(ChangeReason entity) {
        if (entity == null) return null;
        return new ChangeReasonResponse(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getAffectsWorkflow(),
            entity.getCategory(),
            entity.getIsActive()
        );
    }
}
