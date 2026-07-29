package az.millers.hcm.recruitment.domain;

import java.math.BigDecimal;
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
 * M287 — Recruitment PRD §22: an assessment/test assigned to an
 * application (technical, language, cognitive, …). Manual result
 * entry; provider + externalRef reserve space for a later
 * external-provider integration.
 */
@Entity
@Table(name = "assessment", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Assessment {

    public enum Type {
        TECHNICAL, LANGUAGE, COGNITIVE, PERSONALITY, JOB_SIMULATION,
        PRACTICAL, CASE_STUDY, TYPING, OTHER
    }

    public enum Status { ASSIGNED, IN_PROGRESS, COMPLETED, EXPIRED, CANCELLED }

    public enum Result { PASS, FAIL }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "assessment_no", nullable = false, unique = true)
    private String assessmentNo;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 24)
    private Type assessmentType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 160)
    private String provider;

    @Column(name = "external_ref", length = 160)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ASSIGNED;

    @Column(precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", precision = 6, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "passing_score", precision = 6, scale = 2)
    private BigDecimal passingScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Result result;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "attachment_id")
    private UUID attachmentId;

    /** Whether a FAIL here blocks the hire (PRD §22 / §70). */
    @Column(name = "blocks_hire", nullable = false)
    private boolean blocksHire;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (assignedAt == null) assignedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
