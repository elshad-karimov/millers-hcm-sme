package az.millers.hcm.attachment.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attachment.domain.Attachment;

public record AttachmentResponse(
        UUID id,
        String attachmentNo,
        String bucket,
        String objectKey,
        /** M36: present iff a pre-baked 256-px JPEG thumbnail exists. */
        boolean hasThumbnail,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String ownerModule,
        String ownerEntity,
        UUID ownerId,
        String uploadedBy,
        OffsetDateTime uploadedAt) {

    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(
                a.getId(), a.getAttachmentNo(), a.getBucket(), a.getObjectKey(),
                a.getThumbObjectKey() != null,
                a.getOriginalFilename(), a.getContentType(), a.getSizeBytes(),
                a.getOwnerModule(), a.getOwnerEntity(), a.getOwnerId(),
                a.getUploadedBy(), a.getUploadedAt());
    }
}
