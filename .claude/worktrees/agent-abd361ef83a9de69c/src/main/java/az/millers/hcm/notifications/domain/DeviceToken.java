package az.millers.hcm.notifications.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FCM device token registered by the Flutter mobile app (PRD §17.5 / M51).
 * A single user may have multiple tokens (multiple devices / reinstalls).
 */
@Entity
@Table(name = "device_token", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "TEXT")
    private String fcmToken;

    @Column(nullable = false, length = 10)
    private String platform = "FLUTTER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
