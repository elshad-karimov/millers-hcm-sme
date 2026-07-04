package az.millers.hcm.apikey.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M120 — an issued API key. The plaintext value is shown exactly once to
 * the caller of {@link az.millers.hcm.apikey.service.ApiKeyService#issue};
 * the row carries only the SHA-256 digest so the DB is useless to a
 * passive attacker.
 *
 * <p>{@code scopes} is the set of {@code ROLE_*} authority names this
 * key is allowed to act under — the auth filter sets these on the
 * resulting {@link org.springframework.security.core.Authentication}
 * exactly as the JWT path would.
 */
@Entity
@Table(name = "api_key", schema = "security")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "owner_user", nullable = false, length = 160)
    private String ownerUser;

    /** SHA-256 hex of the plaintext key. */
    @Column(name = "key_hash", nullable = false, length = 64, updatable = false)
    private String keyHash;

    /** Last 4 chars of the plaintext — purely a UI fingerprint. */
    @Column(nullable = false, length = 4, updatable = false)
    private String last4;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes_json", nullable = false)
    private List<String> scopes;

    @Column(name = "rate_limit_per_min", nullable = false)
    private int rateLimitPerMin = 60;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "last_used_ip", length = 64)
    private String lastUsedIp;

    @Column(name = "usage_count", nullable = false)
    private long usageCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by", length = 160)
    private String revokedBy;

    @Column(name = "revoke_reason", columnDefinition = "text")
    private String revokeReason;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    /** Active, not revoked, not past its expiry. The auth filter's gate. */
    public boolean isUsable(OffsetDateTime now) {
        if (!active || revokedAt != null) return false;
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
