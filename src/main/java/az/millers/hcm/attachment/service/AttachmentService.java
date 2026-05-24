package az.millers.hcm.attachment.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

import az.millers.hcm.attachment.api.AttachmentResponse;
import az.millers.hcm.attachment.domain.Attachment;
import az.millers.hcm.attachment.domain.ScanStatus;
import az.millers.hcm.attachment.repo.AttachmentRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;

/**
 * Stores file attachments in MinIO (PRD 14.6 + 16.4). Each upload writes a
 * row to {@code core_hr.attachment} and a corresponding object in the
 * configured bucket. The {@code object_key} is namespaced
 * {@code <module>/<entity>/<ownerId>/<uuid>-<filename>} so that a
 * MinIO-side listing is human-readable too.
 *
 * <p>MinIO is optional: if the bean isn't present (storage disabled), all
 * write operations 503 and reads of existing rows still succeed.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final String MODULE = "ATTACHMENT";

    private final AttachmentRepository attachments;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final MinioClient minioClient;          // may be null when storage disabled
    private final ThumbnailService thumbnailService;
    private final ClamAvScanService scanner;
    private final String bucket;
    private final long maxBytes;
    private final int presignedUrlExpiryMinutes;

    public AttachmentService(AttachmentRepository attachments,
                             AuditService audit,
                             CurrentRequest currentRequest,
                             @Autowired(required = false) MinioClient minioClient,
                             ThumbnailService thumbnailService,
                             ClamAvScanService scanner,
                             @Value("${hcm.storage.minio.bucket:hcm-attachments}") String bucket,
                             @Value("${hcm.storage.minio.max-file-bytes:20971520}") long maxBytes,
                             @Value("${hcm.storage.minio.presigned-url-expiry-minutes:15}") int presignedUrlExpiryMinutes) {
        this.attachments = attachments;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.minioClient = minioClient;
        this.thumbnailService = thumbnailService;
        this.scanner = scanner;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
        this.presignedUrlExpiryMinutes = presignedUrlExpiryMinutes;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<Attachment> list(String ownerModule, String ownerEntity, UUID ownerId) {
        if (ownerId == null) {
            throw new BadRequestException("ownerId is required");
        }
        return attachments
                .findByOwnerModuleAndOwnerEntityAndOwnerIdAndDeletedFalseOrderByUploadedAtDesc(
                        normalise(ownerModule), normalise(ownerEntity), ownerId);
    }

    @Transactional(readOnly = true)
    public Attachment get(UUID id) {
        return attachments.findById(id)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + id));
    }

    // ---------------------------------------------------------------- upload

    /**
     * Byte-array variant for server-generated payloads (e.g. report exports).
     * Bypasses MultipartFile so internal callers don't need to wrap byte[]
     * just to satisfy the controller's signature.
     */
    @Transactional
    public Attachment uploadBytes(String ownerModule, String ownerEntity, UUID ownerId,
                                    byte[] payload, String filename, String contentType) {
        ensureMinio();
        if (payload == null || payload.length == 0) {
            throw new BadRequestException("payload is required and must be non-empty");
        }
        if (payload.length > maxBytes) {
            throw new BadRequestException(
                    "File exceeds the configured max size (" + maxBytes + " bytes)");
        }
        if (ownerId == null) {
            throw new BadRequestException("ownerId is required");
        }
        String safeFilename = sanitiseFilename(Objects.requireNonNullElse(filename, "upload.bin"));
        String mod = normalise(ownerModule);
        String ent = normalise(ownerEntity);
        String key = mod.toLowerCase() + "/" + ent.toLowerCase() + "/" + ownerId + "/"
                + UUID.randomUUID() + "-" + safeFilename;
        String ct = Objects.requireNonNullElse(contentType, "application/octet-stream");

        try (InputStream in = new java.io.ByteArrayInputStream(payload)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, payload.length, -1)
                    .contentType(ct)
                    .build());
        } catch (Exception e) {
            log.error("MinIO putObject failed", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Storage unavailable: " + e.getMessage(), e);
        }

        // M50: server-generated payloads are trusted — mark as SKIPPED rather
        // than running them through ClamAV (which would always be clean anyway).
        ScanStatus scanStatus = ScanStatus.SKIPPED;

        // M36: bake a 256-px JPEG thumbnail for image content types.
        // Failure to thumbnail does not fail the upload — the row
        // still saves with thumb_object_key=NULL and the /thumbnail
        // endpoint falls back to the original blob.
        String thumbKey = maybeStoreThumbnail(key, payload, ct);

        Attachment a = new Attachment();
        a.setAttachmentNo(String.format("ATT-%05d", attachments.nextNoSequence()));
        a.setBucket(bucket);
        a.setObjectKey(key);
        a.setThumbObjectKey(thumbKey);
        a.setOriginalFilename(safeFilename);
        a.setContentType(ct);
        a.setSizeBytes((long) payload.length);
        a.setOwnerModule(mod);
        a.setOwnerEntity(ent);
        a.setOwnerId(ownerId);
        a.setUploadedBy(currentRequest.username());
        a.setDeleted(false);
        a.setScanStatus(scanStatus);
        Attachment saved = attachments.save(a);

        audit.record(MODULE, "Attachment", saved.getId().toString(),
                "UPLOAD", null, AttachmentResponse.from(saved));
        return saved;
    }

    @Transactional
    public Attachment upload(String ownerModule, String ownerEntity, UUID ownerId, MultipartFile file) {
        ensureMinio();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required and must be non-empty");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(
                    "File exceeds the configured max size (" + maxBytes + " bytes)");
        }
        if (ownerId == null) {
            throw new BadRequestException("ownerId is required");
        }
        String safeFilename = sanitiseFilename(
                Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin"));

        String mod = normalise(ownerModule);
        String ent = normalise(ownerEntity);
        String key = mod.toLowerCase() + "/" + ent.toLowerCase() + "/" + ownerId + "/"
                + UUID.randomUUID() + "-" + safeFilename;
        String ct = Objects.requireNonNullElse(file.getContentType(), "application/octet-stream");

        // Read once, both for MinIO upload + thumbnail generation. The
        // existing maxBytes guard above caps this — at 20 MiB default
        // it's safe to materialise in memory.
        byte[] payload;
        try {
            payload = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read MultipartFile", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to read upload: " + e.getMessage(), e);
        }

        try (InputStream in = new java.io.ByteArrayInputStream(payload)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, payload.length, -1)
                    .contentType(ct)
                    .build());
        } catch (Exception e) {
            log.error("MinIO putObject failed", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Storage unavailable: " + e.getMessage(), e);
        }

        // M50 (PRD 14.8): virus-scan user-uploaded bytes via ClamAV INSTREAM.
        // If infected: delete the object we just uploaded and reject the request.
        // If scan is disabled: mark SKIPPED. If clamd is unreachable: 503.
        ScanStatus scanStatus;
        OffsetDateTime scanAt = null;
        if (scanner.isEnabled()) {
            try {
                ClamAvScanService.ScanResult result = scanner.scan(payload);
                if (result.isInfected()) {
                    // Quarantine: remove the just-uploaded object so it can't
                    // be accessed even if the exception is somehow swallowed.
                    removeQuietly(key);
                    throw new BadRequestException(
                            "Upload rejected: antivirus detected threat"
                            + (result.detail() != null ? " (" + result.detail() + ")" : ""));
                }
                scanStatus = result.isClean() ? ScanStatus.CLEAN : ScanStatus.SKIPPED;
                scanAt     = OffsetDateTime.now();
            } catch (java.io.IOException e) {
                // ClamAV unreachable — fail safe: block the upload
                log.error("ClamAV unavailable at {}:{}: {}", scanner.isEnabled(), e.getMessage(), e);
                removeQuietly(key);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Virus scanner unavailable — upload blocked for safety: " + e.getMessage(), e);
            }
        } else {
            scanStatus = ScanStatus.SKIPPED;
        }

        String thumbKey = maybeStoreThumbnail(key, payload, ct);

        Attachment a = new Attachment();
        a.setAttachmentNo(String.format("ATT-%05d", attachments.nextNoSequence()));
        a.setBucket(bucket);
        a.setObjectKey(key);
        a.setThumbObjectKey(thumbKey);
        a.setOriginalFilename(safeFilename);
        a.setContentType(file.getContentType());
        a.setSizeBytes((long) payload.length);
        a.setOwnerModule(mod);
        a.setOwnerEntity(ent);
        a.setOwnerId(ownerId);
        a.setUploadedBy(currentRequest.username());
        a.setDeleted(false);
        a.setScanStatus(scanStatus);
        a.setScanAt(scanAt);
        Attachment saved = attachments.save(a);

        audit.record(MODULE, "Attachment", saved.getId().toString(),
                "UPLOAD", null, AttachmentResponse.from(saved));
        return saved;
    }

    // -------------------------------------------------------------- download

    /** Streams the underlying MinIO object to the caller. Closes the stream. */
    public GetObjectResponse download(UUID attachmentId) {
        ensureMinio();
        Attachment a = get(attachmentId);
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(a.getBucket())
                    .object(a.getObjectKey())
                    .build());
        } catch (Exception e) {
            log.error("MinIO getObject failed for {}", a.getObjectKey(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Storage unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Streams the 256-px JPEG thumbnail (M36) when one was pre-baked;
     * falls back to the original blob otherwise so the
     * {@code /thumbnail} endpoint stays backward-compatible with
     * pre-M36 rows + non-image content types.
     *
     * @return the stream + a flag indicating whether it's the thumb
     *         (true) or the original (false) — the controller uses
     *         this to set {@code Content-Type: image/jpeg} vs.
     *         {@code Content-Type: a.contentType}.
     */
    public ThumbnailDownload downloadThumbnail(UUID attachmentId) {
        ensureMinio();
        Attachment a = get(attachmentId);
        String thumbKey = a.getThumbObjectKey();
        if (thumbKey == null) {
            // No pre-baked thumb — return the original blob so the
            // browser still has something to render.
            return new ThumbnailDownload(download(attachmentId), false, a);
        }
        try {
            GetObjectResponse stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(a.getBucket())
                    .object(thumbKey)
                    .build());
            return new ThumbnailDownload(stream, true, a);
        } catch (Exception e) {
            // Thumb row says one exists but MinIO can't find it —
            // log + fall back to the original. Cleanest UX: never
            // 500 the preview path.
            log.warn("MinIO getObject failed for thumb {}: {} — falling back to original",
                    thumbKey, e.getMessage());
            return new ThumbnailDownload(download(attachmentId), false, a);
        }
    }

    /**
     * Envelope returned by {@link #downloadThumbnail(UUID)}. Carries
     * the resolved stream + the original {@link Attachment} so the
     * controller can pick the right {@code Content-Type} without
     * re-fetching the row.
     */
    public record ThumbnailDownload(GetObjectResponse stream, boolean isThumb, Attachment attachment) { }

    // ------------------------------------------------------ presigned URL (M50)

    /**
     * Generates a MinIO presigned GET URL for {@code attachmentId} that is
     * valid for {@link #presignedUrlExpiryMinutes} minutes (default 15).
     *
     * <p>The caller has already authenticated with a Bearer JWT; this method
     * translates that into a short-lived object-storage credential so the
     * browser can download the file directly from MinIO without streaming
     * through the backend (PRD 14.8 — "accessed only through signed,
     * time-limited URLs").
     *
     * @return the presigned URL string — never null
     */
    public String getPresignedUrl(UUID attachmentId) {
        ensureMinio();
        Attachment a = get(attachmentId);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(a.getBucket())
                            .object(a.getObjectKey())
                            .expiry(presignedUrlExpiryMinutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}", a.getObjectKey(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Storage unavailable: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- delete

    @Transactional
    public Attachment delete(UUID attachmentId) {
        Attachment a = get(attachmentId);
        AttachmentResponse before = AttachmentResponse.from(a);
        // Soft-delete metadata; physically remove from MinIO so we don't pay
        // for orphan bytes. If MinIO is offline, soft-delete still succeeds.
        if (minioClient != null) {
            removeQuietly(a.getObjectKey());
            // M36: also clean up the pre-baked thumbnail sibling. Same
            // tolerance — log + carry on if it's gone or unreachable.
            if (a.getThumbObjectKey() != null) {
                removeQuietly(a.getThumbObjectKey());
            }
        }
        a.setDeleted(true);
        a.setDeletedAt(OffsetDateTime.now());
        a.setDeletedBy(currentRequest.username());
        Attachment saved = attachments.save(a);
        audit.record(MODULE, "Attachment", attachmentId.toString(),
                "DELETE", before, null);
        return saved;
    }

    private void removeQuietly(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO removeObject failed for {} — keeping orphan: {}",
                    key, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    private void ensureMinio() {
        if (minioClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "File storage is not configured on this server");
        }
    }

    private static String normalise(String s) {
        if (s == null || s.isBlank()) {
            throw new BadRequestException("module/entity must be non-empty");
        }
        return s.trim();
    }

    /** Strip path components + control chars; cap length. */
    static String sanitiseFilename(String raw) {
        if (raw == null) return "upload.bin";
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String base = slash >= 0 ? raw.substring(slash + 1) : raw;
        // Replace anything not in a safe whitelist with underscore.
        String cleaned = base.replaceAll("[^A-Za-z0-9._\\-]", "_");
        if (cleaned.isBlank()) cleaned = "upload.bin";
        if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200);
        return cleaned;
    }

    /** I/O helper for the controller to read a stream once. */
    public static byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    /**
     * Generate + upload a 256-px JPEG thumbnail for the just-stored
     * object. Returns the new thumbnail's MinIO key, or {@code null}
     * when the content type isn't thumbnailable, generation failed,
     * or the thumbnail upload itself errored (the original upload
     * always wins — pre-baked thumbs are a perf optimisation, not a
     * correctness requirement).
     *
     * <p>Key convention: the original object key with a {@code .thumb.jpg}
     * suffix. That keeps thumbnails namespaced under the same
     * {@code <module>/<entity>/<ownerId>/} prefix as their parent, so
     * a MinIO listing reads naturally + per-owner lifecycle policies
     * still apply.
     */
    String maybeStoreThumbnail(String originalKey, byte[] payload, String contentType) {
        if (!thumbnailService.isThumbnailable(contentType)) return null;
        byte[] thumb = thumbnailService.generate(payload, contentType);
        if (thumb == null) return null;
        String thumbKey = originalKey + ".thumb.jpg";
        try (InputStream in = new java.io.ByteArrayInputStream(thumb)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(thumbKey)
                    .stream(in, thumb.length, -1)
                    .contentType("image/jpeg")
                    .build());
            return thumbKey;
        } catch (Exception e) {
            // The original blob already uploaded — best we can do here
            // is log + return null. The /thumbnail endpoint will fall
            // back to the original for previews.
            log.warn("MinIO putObject failed for thumbnail {}: {} — original kept",
                    thumbKey, e.getMessage());
            return null;
        }
    }
}
