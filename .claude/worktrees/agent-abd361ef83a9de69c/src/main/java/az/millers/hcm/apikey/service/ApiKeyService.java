package az.millers.hcm.apikey.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.apikey.api.ApiKeyDtos.ApiKeySummary;
import az.millers.hcm.apikey.api.ApiKeyDtos.IssueRequest;
import az.millers.hcm.apikey.api.ApiKeyDtos.IssueResponse;
import az.millers.hcm.apikey.api.ApiKeyDtos.UsageBucket;
import az.millers.hcm.apikey.api.ApiKeyDtos.UsageResponse;
import az.millers.hcm.apikey.domain.ApiKey;
import az.millers.hcm.apikey.repo.ApiKeyRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;

/**
 * M120 — admin-side lifecycle for API keys. The hot-path authentication
 * (lookup by hash, scope expansion, rate-limit check) lives in
 * {@link az.millers.hcm.apikey.security.ApiKeyAuthFilter} so this
 * service can stay transactional and audited.
 *
 * <p>Defaults: rate-limit 60 req/min, no expiry, all scopes the owner
 * lists in the request — bounded by {@link ApiKeyScopes#ALL_SCOPES}.
 */
@Service
public class ApiKeyService {

    private static final int DEFAULT_RATE_LIMIT = 60;

    private final ApiKeyRepository repo;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ApiKeyService(ApiKeyRepository repo,
                         NamedParameterJdbcTemplate jdbc,
                         AuditService audit,
                         CurrentRequest currentRequest) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Issue / revoke ─────────────────────────────────────────────────────

    @Transactional
    public IssueResponse issue(IssueRequest req) {
        if (req.label() == null || req.label().isBlank()) {
            throw new BadRequestException("Label is required");
        }
        List<String> scopes = ApiKeyScopes.normalise(req.scopes());
        int rate = req.rateLimitPerMin() == null ? DEFAULT_RATE_LIMIT : req.rateLimitPerMin();
        if (rate < 1 || rate > 10_000) {
            throw new BadRequestException("Rate limit must be between 1 and 10000 requests/min");
        }
        if (req.expiresAt() != null && req.expiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Expiry must be in the future");
        }

        String plaintext = ApiKeyCrypto.generatePlaintext();
        ApiKey key = new ApiKey();
        key.setLabel(req.label().trim());
        key.setDescription(req.description());
        key.setOwnerUser(currentRequest.username());
        key.setKeyHash(ApiKeyCrypto.hash(plaintext));
        key.setLast4(ApiKeyCrypto.last4(plaintext));
        key.setScopes(scopes);
        key.setRateLimitPerMin(rate);
        key.setExpiresAt(req.expiresAt());
        key.setCreatedBy(currentRequest.username());
        ApiKey saved = repo.save(key);

        // Hash + last4 only — never log/audit the plaintext.
        audit.record("security", "ApiKey", saved.getId().toString(), "ISSUE",
                null,
                Map.of(
                    "label", saved.getLabel(),
                    "scopes", saved.getScopes(),
                    "rateLimitPerMin", saved.getRateLimitPerMin(),
                    "last4", saved.getLast4()));
        return new IssueResponse(toSummary(saved), plaintext);
    }

    @Transactional
    public ApiKeySummary revoke(UUID id, String reason) {
        ApiKey key = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
        if (key.getRevokedAt() != null) {
            throw new BadRequestException("API key already revoked");
        }
        Map<String, Object> before = Map.of("active", key.isActive(), "revokedAt", false);
        key.setActive(false);
        key.setRevokedAt(OffsetDateTime.now());
        key.setRevokedBy(currentRequest.username());
        key.setRevokeReason(reason);
        ApiKey saved = repo.save(key);
        // Bust the in-memory bucket so a revoked key doesn't keep its quota.
        TokenBucket.evict(id);
        audit.record("security", "ApiKey", id.toString(), "REVOKE", before,
                Map.of("revokedBy", saved.getRevokedBy(), "reason", reason));
        return toSummary(saved);
    }

    // ── Read ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ApiKeySummary> listAll() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(ApiKeyService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummary> listMine() {
        return repo.findByOwnerUserOrderByCreatedAtDesc(currentRequest.username())
                .stream().map(ApiKeyService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ApiKeySummary get(UUID id) {
        return repo.findById(id).map(ApiKeyService::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));
    }

    @Transactional(readOnly = true)
    public UsageResponse usage(UUID id, int hours) {
        repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("API key not found: " + id));
        int clampedHours = Math.max(1, Math.min(hours, 168));
        OffsetDateTime from = OffsetDateTime.now().minusHours(clampedHours);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT minute_bucket, request_count, rejected_count "
                + "  FROM security.api_key_usage_minute "
                + " WHERE api_key_id = :id AND minute_bucket >= :from "
                + " ORDER BY minute_bucket DESC",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("from", from));
        long total = 0, rejected = 0;
        List<UsageBucket> buckets = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            int rc = ((Number) row.get("request_count")).intValue();
            int rj = ((Number) row.get("rejected_count")).intValue();
            total += rc;
            rejected += rj;
            buckets.add(new UsageBucket(
                    ((java.sql.Timestamp) row.get("minute_bucket")).toInstant().atOffset(ZoneOffset.UTC),
                    rc,
                    rj));
        }
        return new UsageResponse(id, buckets, total, rejected);
    }

    // ── Hot-path used by ApiKeyAuthFilter ───────────────────────────────────

    /**
     * Plaintext → {@link ApiKey} resolution. Returns {@code null} if the
     * hash isn't known or the key is unusable. NEVER throws — auth-path
     * code is expected to translate {@code null} into a 401.
     */
    @Transactional(readOnly = true)
    public ApiKey resolve(String plaintext) {
        if (!ApiKeyCrypto.looksValid(plaintext)) return null;
        String hash = ApiKeyCrypto.hash(plaintext);
        ApiKey k = repo.findByKeyHash(hash).orElse(null);
        if (k == null) return null;
        return k.isUsable(OffsetDateTime.now()) ? k : null;
    }

    /**
     * Record one successful (or rejected, if {@code accepted == false})
     * API call. Bumps last_used_at + usage_count and rolls up the minute
     * bucket. Best-effort — failures here don't break the request.
     */
    @Transactional
    public void recordUsage(UUID keyId, String ip, boolean accepted) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime bucket = now.truncatedTo(ChronoUnit.MINUTES);
        Map<String, Object> p = new HashMap<>();
        p.put("id", keyId);
        p.put("ip", ip);
        p.put("now", now);
        p.put("bucket", bucket);
        if (accepted) {
            jdbc.update(
                "UPDATE security.api_key SET "
                + "  last_used_at = :now, last_used_ip = :ip, usage_count = usage_count + 1 "
                + "WHERE id = :id",
                new MapSqlParameterSource(p));
            jdbc.update(
                "INSERT INTO security.api_key_usage_minute (api_key_id, minute_bucket, request_count, rejected_count) "
                + "VALUES (:id, :bucket, 1, 0) "
                + "ON CONFLICT (api_key_id, minute_bucket) "
                + "DO UPDATE SET request_count = security.api_key_usage_minute.request_count + 1",
                new MapSqlParameterSource(p));
        } else {
            jdbc.update(
                "INSERT INTO security.api_key_usage_minute (api_key_id, minute_bucket, request_count, rejected_count) "
                + "VALUES (:id, :bucket, 0, 1) "
                + "ON CONFLICT (api_key_id, minute_bucket) "
                + "DO UPDATE SET rejected_count = security.api_key_usage_minute.rejected_count + 1",
                new MapSqlParameterSource(p));
        }
    }

    // ── Mapping ────────────────────────────────────────────────────────────

    private static ApiKeySummary toSummary(ApiKey k) {
        return new ApiKeySummary(
                k.getId(),
                k.getLabel(),
                k.getDescription(),
                k.getOwnerUser(),
                k.getLast4(),
                k.getScopes(),
                k.getRateLimitPerMin(),
                k.isActive(),
                k.getExpiresAt(),
                k.getLastUsedAt(),
                k.getLastUsedIp(),
                k.getUsageCount(),
                k.getCreatedAt(),
                k.getRevokedAt(),
                k.getRevokeReason());
    }
}
