package az.millers.hcm.notifications.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationLog;

/**
 * Read-only projection of {@link NotificationLog} for the REST API.
 */
public record NotificationResponse(
        UUID id,
        String title,
        String body,
        String module,
        String entityType,
        String entityId,
        OffsetDateTime readAt,
        OffsetDateTime createdAt,
        NotificationChannel channel) {

    public static NotificationResponse from(NotificationLog n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getModule(),
                n.getEntityType(),
                n.getEntityId(),
                n.getReadAt(),
                n.getCreatedAt(),
                n.getChannel());
    }
}
