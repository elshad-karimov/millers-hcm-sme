package az.millers.hcm.permission.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.permission.domain.PermissionType;

public record PermissionTypeResponse(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal annualLimitHours,
        boolean paid,
        boolean requiresAttachment,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static PermissionTypeResponse from(PermissionType t) {
        return new PermissionTypeResponse(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getAnnualLimitHours(), t.isPaid(), t.isRequiresAttachment(),
                t.isActive(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
