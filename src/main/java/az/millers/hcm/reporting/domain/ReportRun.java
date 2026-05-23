package az.millers.hcm.reporting.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

@Entity
@Table(name = "report_run", schema = "reporting")
@Getter
@Setter
@NoArgsConstructor
public class ReportRun {

    @Id
    private UUID id;

    @Column(name = "run_no", nullable = false, unique = true)
    private String runNo;

    @Column(name = "definition_id")
    private UUID definitionId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ReportFormat format;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parameters;

    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportRunStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 16)
    private TriggerSource triggerSource;

    /** Delivery state for the generated file. See {@link EmailStatus}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 16)
    private EmailStatus emailStatus;

    /** Snapshot of the recipient list at delivery time. Comma-separated. */
    @Column(name = "email_recipients", columnDefinition = "text")
    private String emailRecipients;

    @Column(name = "email_sent_at")
    private OffsetDateTime emailSentAt;

    @Column(name = "email_error", columnDefinition = "text")
    private String emailError;

    /** Webhook delivery state (PRD 10.5 — Slack / Teams transport). */
    @Enumerated(EnumType.STRING)
    @Column(name = "webhook_status", nullable = false, length = 16)
    private WebhookStatus webhookStatus;

    /**
     * Snapshot of "{TYPE} url" at delivery time. Read-only history.
     * Encrypted at rest (PRD 14.3) — carries the same secret as
     * {@code report_schedule.webhook_url}.
     */
    @jakarta.persistence.Convert(converter =
            az.millers.hcm.security.crypto.EncryptedStringConverter.class)
    @Column(name = "webhook_target", columnDefinition = "text")
    private String webhookTarget;

    @Column(name = "webhook_sent_at")
    private OffsetDateTime webhookSentAt;

    @Column(name = "webhook_error", columnDefinition = "text")
    private String webhookError;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
        if (status == null) status = ReportRunStatus.RUNNING;
        if (triggerSource == null) triggerSource = TriggerSource.MANUAL;
        if (emailStatus == null) emailStatus = EmailStatus.NOT_REQUESTED;
        if (webhookStatus == null) webhookStatus = WebhookStatus.NOT_REQUESTED;
    }
}
