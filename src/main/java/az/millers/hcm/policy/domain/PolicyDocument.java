package az.millers.hcm.policy.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

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
 * M138 — a single version of a company policy. Versions share
 * {@link #code} but each new draft bumps {@link #version} and gets a
 * fresh id. Once {@link #status} flips to {@link PolicyStatus#PUBLISHED}
 * the row is immutable except for an ARCHIVE transition — enforced at
 * the service layer.
 */
@Entity
@Table(name = "policy_document", schema = "policy")
@Getter
@Setter
@NoArgsConstructor
public class PolicyDocument {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /** Stable identifier across versions (e.g. CODE-OF-CONDUCT). */
    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", nullable = false, length = 20)
    private PolicyBodyFormat bodyFormat;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status = PolicyStatus.DRAFT;

    /** When TRUE, employees must explicitly acknowledge this version. */
    @Column(name = "requires_ack", nullable = false)
    private boolean requiresAck = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
