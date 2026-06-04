package az.millers.hcm.audit.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.audit.domain.AuditLog;

/** DTOs for the M114 audit-log browser. */
public final class AuditLogDtos {

    private AuditLogDtos() {}

    /** Slim row used by the table — old/new JSON omitted for payload size. */
    public record AuditLogRow(
            UUID id,
            OffsetDateTime createdAt,
            String actor,
            String module,
            String entityName,
            String entityId,
            String action,
            String ipAddress,
            boolean hasOldValue,
            boolean hasNewValue) {

        public static AuditLogRow from(AuditLog a) {
            return new AuditLogRow(
                    a.getId(),
                    a.getCreatedAt(),
                    a.getActor(),
                    a.getModule(),
                    a.getEntityName(),
                    a.getEntityId(),
                    a.getAction(),
                    a.getIpAddress(),
                    a.getOldValue() != null,
                    a.getNewValue() != null);
        }
    }

    /** Full entry returned by the detail endpoint with both JSON blobs. */
    public record AuditLogDetail(
            UUID id,
            OffsetDateTime createdAt,
            String actor,
            String module,
            String entityName,
            String entityId,
            String action,
            String ipAddress,
            String oldValue,
            String newValue) {

        public static AuditLogDetail from(AuditLog a) {
            return new AuditLogDetail(
                    a.getId(),
                    a.getCreatedAt(),
                    a.getActor(),
                    a.getModule(),
                    a.getEntityName(),
                    a.getEntityId(),
                    a.getAction(),
                    a.getIpAddress(),
                    a.getOldValue(),
                    a.getNewValue());
        }
    }
}
