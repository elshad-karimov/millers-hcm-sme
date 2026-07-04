package az.millers.hcm.backup.api;

import az.millers.hcm.backup.domain.BackupLog;
import az.millers.hcm.backup.domain.BackupStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response DTO for a backup log entry.
 */
public record BackupResponse(
        UUID id,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        BackupStatus status,
        String objectKey,
        Long sizeBytes,
        String checksumSha256,
        String errorMessage,
        String triggeredBy
) {
    public static BackupResponse from(BackupLog log) {
        return new BackupResponse(
                log.getId(),
                log.getStartedAt(),
                log.getFinishedAt(),
                log.getStatus(),
                log.getObjectKey(),
                log.getSizeBytes(),
                log.getChecksumSha256(),
                log.getErrorMessage(),
                log.getTriggeredBy()
        );
    }
}
