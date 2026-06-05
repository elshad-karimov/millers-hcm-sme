package az.millers.hcm.apikey.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * M120 — wire DTOs for the API key admin surface. The plaintext key is
 * surfaced exactly once in {@link IssueResponse}; subsequent reads only
 * see the last4 fingerprint.
 */
public final class ApiKeyDtos {

    private ApiKeyDtos() {}

    public record IssueRequest(
            String label,
            String description,
            List<String> scopes,
            Integer rateLimitPerMin,
            OffsetDateTime expiresAt) {}

    public record RevokeRequest(String reason) {}

    /** Returned once from POST /api-keys. The {@code plaintextKey} is unrecoverable after this response. */
    public record IssueResponse(
            ApiKeySummary summary,
            String plaintextKey) {}

    public record ApiKeySummary(
            UUID id,
            String label,
            String description,
            String ownerUser,
            String last4,
            List<String> scopes,
            int rateLimitPerMin,
            boolean active,
            OffsetDateTime expiresAt,
            OffsetDateTime lastUsedAt,
            String lastUsedIp,
            long usageCount,
            OffsetDateTime createdAt,
            OffsetDateTime revokedAt,
            String revokeReason) {}

    public record UsageBucket(
            OffsetDateTime minuteBucket,
            int requestCount,
            int rejectedCount) {}

    public record UsageResponse(
            UUID apiKeyId,
            List<UsageBucket> buckets,
            long totalRequests,
            long totalRejected) {}
}
