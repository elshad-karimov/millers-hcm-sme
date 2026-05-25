package az.millers.hcm.backup.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistent record for each database backup attempt (PRD §14.4 / M53).
 *
 * <p>Maps to {@code backup.backup_log}. Statuses flow:
 * {@code RUNNING → SUCCESS | FAILED}, and then {@code SUCCESS → VERIFIED}
 * after integrity verification.
 */
@Entity
@Table(schema = "backup", name = "backup_log")
@Getter
@Setter
public class BackupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackupStatus status = BackupStatus.RUNNING;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "triggered_by", nullable = false, length = 255)
    private String triggeredBy = "scheduler";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
