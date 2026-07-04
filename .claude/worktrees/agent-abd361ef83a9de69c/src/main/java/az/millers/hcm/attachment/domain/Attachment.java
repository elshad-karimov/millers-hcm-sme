package az.millers.hcm.attachment.domain;

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

@Entity
@Table(name = "attachment", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class Attachment {

    @Id
    private UUID id;

    @Column(name = "attachment_no", nullable = false, unique = true)
    private String attachmentNo;

    @Column(nullable = false, length = 120)
    private String bucket;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    /**
     * MinIO object key for the 256-px JPEG thumbnail sibling object,
     * or {@code null} for non-image uploads / pre-M36 rows. Populated
     * by {@link az.millers.hcm.attachment.service.ThumbnailService}
     * during {@code AttachmentService.upload()} for image content
     * types (PRD 14.6 / 16.4 — milestone 36).
     */
    @Column(name = "thumb_object_key", length = 500)
    private String thumbObjectKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 160)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "owner_module", nullable = false, length = 40)
    private String ownerModule;

    @Column(name = "owner_entity", nullable = false, length = 60)
    private String ownerEntity;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    /**
     * M50 (PRD 14.8): ClamAV scan outcome. Default {@code PENDING}; resolved
     * to {@code CLEAN} or {@code SKIPPED} before the row is committed.
     * {@code INFECTED} rows are never committed — the upload is rejected first.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 20)
    private ScanStatus scanStatus = ScanStatus.PENDING;

    /** Timestamp when the scan completed (null for PENDING / SKIPPED). */
    @Column(name = "scan_at")
    private OffsetDateTime scanAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (uploadedAt == null) uploadedAt = OffsetDateTime.now();
        if (scanStatus == null) scanStatus = ScanStatus.PENDING;
    }
}
