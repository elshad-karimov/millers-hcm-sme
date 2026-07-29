package az.millers.hcm.preboarding.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M122 — a pre-boarding invite. Carries only the SHA-256 hash of the
 * plaintext token; the plaintext is shown once from
 * {@link az.millers.hcm.preboarding.service.PreboardingInviteService#issue}
 * and never persisted.
 *
 * <p>{@code payloadJson} stores the candidate's submission verbatim until
 * HR runs {@code complete}, which graduates the data into the Employee
 * record and creates EmergencyContact / Dependent rows.
 */
@Entity
@Table(name = "preboarding_invite", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class PreboardingInvite {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PreboardingStatus status = PreboardingStatus.DRAFT;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by", length = 160)
    private String completedBy;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by", length = 160)
    private String revokedBy;

    @Column(name = "revoke_reason", columnDefinition = "text")
    private String revokeReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private Map<String, Object> payloadJson;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 160, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** True iff candidate-side endpoints are allowed to act on this invite. */
    public boolean isAccessibleToCandidate(OffsetDateTime now) {
        return status.isCandidateAccessible() && expiresAt.isAfter(now);
    }
}
