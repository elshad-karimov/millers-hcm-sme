package az.millers.hcm.preboarding.api;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.preboarding.domain.PreboardingStatus;

/**
 * M122 — wire DTOs for the pre-boarding portal. The plaintext token is
 * returned once from {@link IssueResponse} (HR copies the URL); subsequent
 * reads see only the last 6 chars of the hash for HR identification.
 */
public final class PreboardingDtos {

    private PreboardingDtos() {}

    public record IssueRequest(
            UUID employeeId,
            Integer expiresInDays,
            String note) {}

    public record RevokeRequest(String reason) {}

    public record CompleteRequest(
            /** Which sections to graduate into core_hr. Defaults to all when null. */
            Boolean personalInfo,
            Boolean emergencyContacts,
            Boolean dependents,
            String note) {}

    public record IssueResponse(
            InviteSummary summary,
            String plaintextToken) {}

    public record InviteSummary(
            UUID id,
            UUID employeeId,
            String employeeName,
            String employeeNo,
            PreboardingStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime sentAt,
            OffsetDateTime openedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime completedAt,
            String completedBy,
            OffsetDateTime revokedAt,
            String revokedBy,
            String revokeReason,
            OffsetDateTime createdAt,
            String createdBy,
            String note) {}

    public record InviteDetail(
            InviteSummary summary,
            Map<String, Object> payload) {}

    // ── Public (candidate-facing) DTOs ─────────────────────────────────────

    public record PublicInfo(
            String employeeName,
            String employeeNo,
            PreboardingStatus status,
            OffsetDateTime expiresAt,
            /** Read-only form schema descriptor so the SPA can render. */
            Map<String, Object> schema) {}

    public record SubmitRequest(Map<String, Object> payload) {}

    public record SubmitResponse(PreboardingStatus status, OffsetDateTime submittedAt) {}
}
