package az.millers.hcm.policy.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.policy.domain.PolicyAcknowledgement;
import az.millers.hcm.policy.domain.PolicyBodyFormat;
import az.millers.hcm.policy.domain.PolicyDocument;
import az.millers.hcm.policy.domain.PolicyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M138 — policy library wire DTOs.
 */
public final class PolicyDtos {

    private PolicyDtos() {}

    /**
     * Admin create / update payload. {@code version} is server-managed
     * on create — pass null. Update is restricted to DRAFT rows.
     */
    public record PolicyRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 240) String title,
            @Size(max = 500) String summary,
            @NotBlank @Size(max = 40) String category,
            @NotNull PolicyBodyFormat bodyFormat,
            String bodyText,
            @Size(max = 500) String attachmentUrl,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean requiresAck) {}

    public record PolicyResponse(
            UUID id,
            String code,
            String title,
            String summary,
            String category,
            int version,
            PolicyBodyFormat bodyFormat,
            String bodyText,
            String attachmentUrl,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            PolicyStatus status,
            boolean requiresAck,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static PolicyResponse from(PolicyDocument p) {
            return new PolicyResponse(
                    p.getId(), p.getCode(), p.getTitle(), p.getSummary(), p.getCategory(),
                    p.getVersion(), p.getBodyFormat(), p.getBodyText(), p.getAttachmentUrl(),
                    p.getEffectiveFrom(), p.getEffectiveTo(),
                    p.getStatus(), p.isRequiresAck(),
                    p.getCreatedAt(), p.getCreatedBy(),
                    p.getUpdatedAt(), p.getUpdatedBy());
        }
    }

    /**
     * Self-service browse row — adds {@code acknowledged} +
     * {@code acknowledgedAt} so the SPA can show "✓ acked on …" without
     * an extra round trip.
     */
    public record SelfPolicyView(
            PolicyResponse policy,
            boolean acknowledged,
            OffsetDateTime acknowledgedAt) {}

    public record AcknowledgementResponse(
            UUID id,
            UUID policyId,
            UUID employeeId,
            int versionAcknowledged,
            OffsetDateTime acknowledgedAt,
            String acknowledgedIp) {

        public static AcknowledgementResponse from(PolicyAcknowledgement a) {
            return new AcknowledgementResponse(
                    a.getId(), a.getPolicyId(), a.getEmployeeId(),
                    a.getVersionAcknowledged(), a.getAcknowledgedAt(),
                    a.getAcknowledgedIp());
        }
    }
}
