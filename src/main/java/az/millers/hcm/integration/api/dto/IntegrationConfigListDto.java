package az.millers.hcm.integration.api.dto;

import az.millers.hcm.integration.domain.IntegrationConfig;
import az.millers.hcm.integration.domain.IntegrationDirection;
import az.millers.hcm.integration.domain.IntegrationType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * M492 security: Integration config list DTO with masked endpoint_url.
 * Prevents leaking decrypted URLs in bulk list responses.
 */
public record IntegrationConfigListDto(
    UUID id,
    String code,
    String name,
    IntegrationDirection direction,
    IntegrationType type,
    boolean enabled,
    OffsetDateTime lastRunAt,
    String lastStatus,
    String notes
) {
    public static IntegrationConfigListDto from(IntegrationConfig entity) {
        return new IntegrationConfigListDto(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getDirection(),
            entity.getType(),
            entity.isEnabled(),
            entity.getLastRunAt(),
            entity.getLastStatus(),
            entity.getNotes()
        );
    }
}
