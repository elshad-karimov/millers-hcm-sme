package az.millers.hcm.attachment.api;

import az.millers.hcm.security.SecurityRoles;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import az.millers.hcm.attachment.domain.Attachment;
import az.millers.hcm.attachment.service.AttachmentService;
import io.minio.GetObjectResponse;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService service;
    private final int presignedExpiryMinutes;

    public AttachmentController(
            AttachmentService service,
            @Value("${hcm.storage.minio.presigned-url-expiry-minutes:15}") int presignedExpiryMinutes) {
        this.service = service;
        this.presignedExpiryMinutes = presignedExpiryMinutes;
    }

    /** List attachments owned by (module, entity, ownerId). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AttachmentResponse> list(@RequestParam String ownerModule,
                                          @RequestParam String ownerEntity,
                                          @RequestParam UUID ownerId) {
        return service.list(ownerModule, ownerEntity, ownerId).stream()
                .map(AttachmentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public AttachmentResponse get(@PathVariable UUID id) {
        return AttachmentResponse.from(service.get(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AttachmentResponse upload(@RequestParam String ownerModule,
                                      @RequestParam String ownerEntity,
                                      @RequestParam UUID ownerId,
                                      @RequestParam("file") MultipartFile file) {
        return AttachmentResponse.from(service.upload(ownerModule, ownerEntity, ownerId, file));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws IOException {
        Attachment a = service.get(id);
        GetObjectResponse stream = service.download(id);

        String filename = a.getOriginalFilename() == null ? "attachment.bin" : a.getOriginalFilename();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                .filename(filename)
                .build());
        if (a.getContentType() != null) {
            headers.setContentType(MediaType.parseMediaType(a.getContentType()));
        }
        if (a.getSizeBytes() != null) {
            headers.setContentLength(a.getSizeBytes());
        }
        headers.add("X-Attachment-Filename", encoded);
        return new ResponseEntity<>(new InputStreamResource(stream), headers, HttpStatus.OK);
    }

    /**
     * M36 thumbnail endpoint. Returns the pre-baked 256-px JPEG when
     * {@code thumb_object_key} is populated; falls back to streaming
     * the original blob when not (pre-M36 rows, non-image content,
     * generation failures). The {@code X-Thumb-Source} response header
     * lets the client tell which path served the bytes — useful for
     * debugging + frontend metrics on cache-hit ratio.
     */
    @GetMapping("/{id}/thumbnail")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> thumbnail(@PathVariable UUID id) {
        AttachmentService.ThumbnailDownload td = service.downloadThumbnail(id);
        Attachment a = td.attachment();

        HttpHeaders headers = new HttpHeaders();
        if (td.isThumb()) {
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.add("X-Thumb-Source", "generated");
        } else {
            if (a.getContentType() != null) {
                headers.setContentType(MediaType.parseMediaType(a.getContentType()));
            }
            if (a.getSizeBytes() != null) {
                headers.setContentLength(a.getSizeBytes());
            }
            headers.add("X-Thumb-Source", "original-fallback");
        }
        // Inline (not "attachment") — these are previewed in-page, not downloaded.
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                .filename(a.getOriginalFilename() == null
                        ? "attachment.bin"
                        : a.getOriginalFilename())
                .build());
        return new ResponseEntity<>(new InputStreamResource(td.stream()), headers, HttpStatus.OK);
    }

    /**
     * M50 (PRD 14.8): returns a MinIO presigned GET URL for the attachment,
     * valid for {@code presignedExpiryMinutes} minutes (default 15).
     *
     * <p>The client authenticates once with a Bearer JWT; the response URL is a
     * short-lived credential that lets the browser download the file directly
     * from MinIO without streaming through the backend — satisfying the PRD
     * requirement that files are "accessed only through signed, time-limited URLs."
     *
     * <p>Response shape:
     * <pre>
     * {
     *   "url":            "http://localhost:9000/hcm-attachments/…?X-Amz-…",
     *   "filename":       "quarterly-report.pdf",
     *   "contentType":    "application/pdf",
     *   "expiresInSeconds": 900
     * }
     * </pre>
     */
    @GetMapping("/{id}/url")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> presignedUrl(@PathVariable UUID id) {
        Attachment a = service.get(id);
        String url = service.getPresignedUrl(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("filename", a.getOriginalFilename());
        body.put("contentType", a.getContentType());
        body.put("expiresAt", Instant.now().plus(presignedExpiryMinutes, ChronoUnit.MINUTES).toString());
        body.put("expiresInSeconds", presignedExpiryMinutes * 60);
        return body;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AttachmentResponse delete(@PathVariable UUID id) {
        return AttachmentResponse.from(service.delete(id));
    }
}
