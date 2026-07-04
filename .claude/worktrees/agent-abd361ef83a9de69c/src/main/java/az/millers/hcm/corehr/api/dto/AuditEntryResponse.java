package az.millers.hcm.corehr.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.audit.domain.AuditLog;

public record AuditEntryResponse(
        UUID id,
        String actor,
        String module,
        String action,
        String oldValue,
        String newValue,
        String ipAddress,
        OffsetDateTime createdAt) {

    public static AuditEntryResponse from(AuditLog log) {
        return new AuditEntryResponse(
                log.getId(),
                log.getActor(),
                log.getModule(),
                log.getAction(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getCreatedAt());
    }
}
