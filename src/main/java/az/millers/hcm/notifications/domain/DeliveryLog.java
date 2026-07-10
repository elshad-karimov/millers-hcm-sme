package az.millers.hcm.notifications.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M493 — Notification delivery audit log. All channels (EMAIL, IN_APP, PUSH).
 * Wire inserts into NotificationService + EmailService send paths (non-fatal).
 */
@Entity
@Table(name = "delivery_log", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryLog {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(length = 300)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Column(name = "source_module", length = 60)
    private String sourceModule;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (sentAt == null) sentAt = OffsetDateTime.now();
    }
}
