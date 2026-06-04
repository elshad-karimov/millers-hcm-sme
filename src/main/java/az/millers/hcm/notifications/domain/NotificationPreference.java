package az.millers.hcm.notifications.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user opt-out for a specific (category, channel) pair (M115).
 *
 * <p>Absence of a row is interpreted as opt-IN — the default for every
 * category and every channel. The unique index on (username, category,
 * channel) makes upserts safe at the DB level.
 */
@Entity
@Table(name = "notification_preference", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
