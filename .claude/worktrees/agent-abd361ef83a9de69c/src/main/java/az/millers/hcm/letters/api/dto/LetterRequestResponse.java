package az.millers.hcm.letters.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.letters.domain.LetterRequest;
import az.millers.hcm.letters.domain.LetterStatus;

public record LetterRequestResponse(
        UUID id, String requestNo,
        UUID templateId, UUID employeeId,
        String purpose, Object customFieldsJson,
        LetterStatus status,
        UUID workflowInstanceId,
        String renderedBody,
        UUID attachmentId,
        OffsetDateTime requestedAt, OffsetDateTime issuedAt,
        String decidedBy, String decisionComment,
        // M139 — Phase 2
        String renderedPdfUrl,
        String verificationToken,
        OffsetDateTime verifiedAt,
        String signedBy,
        OffsetDateTime signedAt,
        String language,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static LetterRequestResponse from(LetterRequest r) {
        return new LetterRequestResponse(
                r.getId(), r.getRequestNo(),
                r.getTemplateId(), r.getEmployeeId(),
                r.getPurpose(), r.getCustomFieldsJson(),
                r.getStatus(),
                r.getWorkflowInstanceId(),
                r.getRenderedBody(),
                r.getAttachmentId(),
                r.getRequestedAt(), r.getIssuedAt(),
                r.getDecidedBy(), r.getDecisionComment(),
                r.getRenderedPdfUrl(),
                r.getVerificationToken(),
                r.getVerifiedAt(),
                r.getSignedBy(),
                r.getSignedAt(),
                r.getLanguage(),
                r.getCreatedAt(), r.getCreatedBy(),
                r.getUpdatedAt(), r.getUpdatedBy());
    }
}
