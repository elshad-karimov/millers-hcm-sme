package az.millers.hcm.reporting.domain;

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
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "report_schedule", schema = "reporting")
@Getter
@Setter
@NoArgsConstructor
public class ReportSchedule {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "schedule_no", nullable = false, unique = true)
    private String scheduleNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(nullable = false, length = 120)
    private String cron;

    @Column(columnDefinition = "text")
    private String recipients;

    /** Slack / Teams webhook flavour for this schedule (PRD 10.5). */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "webhook_type", nullable = false, length = 16)
    private WebhookType webhookType = WebhookType.NONE;

    /**
     * Webhook URL — Slack incoming-webhook URL, or Teams connector URL.
     * Encrypted at rest (PRD 14.3) — Slack incoming-webhook URLs are
     * bearer tokens, so leaking a DB dump would hand attackers a way to
     * spam channels until the token's rotated.
     */
    @jakarta.persistence.Convert(converter =
            az.millers.hcm.security.crypto.EncryptedStringConverter.class)
    @Column(name = "webhook_url", columnDefinition = "text")
    private String webhookUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "next_run_at")
    private OffsetDateTime nextRunAt;

    @Column(name = "last_status", length = 16)
    private String lastStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
