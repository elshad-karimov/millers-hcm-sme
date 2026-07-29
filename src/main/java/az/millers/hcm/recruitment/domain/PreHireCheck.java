package az.millers.hcm.recruitment.domain;

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
 * M286 — Recruitment PRD §25-§27: a pre-hire check on an application
 * (background / reference / medical / identity / education / …).
 * One generic shape across all types.
 */
@Entity
@Table(name = "pre_hire_check", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class PreHireCheck {

    public enum Type {
        BACKGROUND, IDENTITY, EDUCATION, EMPLOYMENT, REFERENCE,
        CRIMINAL, CREDIT, LICENSE, WORK_AUTHORIZATION, MEDICAL;

        /** PRD §27 — medical detail is restricted to HR_ADMIN / SYSTEM_ADMIN. */
        public boolean isConfidential() {
            return this == MEDICAL;
        }
    }

    public enum Status {
        NOT_REQUIRED, REQUIRED, REQUESTED, IN_PROGRESS,
        COMPLETED, PASSED, FAILED, REQUIRES_REVIEW, CANCELLED
    }

    public enum Result { PASS, FAIL, CONDITIONAL }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "check_no", nullable = false, unique = true)
    private String checkNo;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 24)
    private Type checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUIRED;

    @Column(length = 160)
    private String provider;

    /** Referee name (reference check) / clinic (medical) / subject. */
    @Column(name = "subject_name", length = 160)
    private String subjectName;

    @Column(name = "subject_contact", length = 160)
    private String subjectContact;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Result result;

    /** Confidential — redacted for MEDICAL unless HR_ADMIN (PRD §27). */
    @Column(name = "result_notes", columnDefinition = "text")
    private String resultNotes;

    @Column(name = "attachment_id")
    private UUID attachmentId;

    /** Whether a FAIL here blocks the hire (PRD §25 business logic). */
    @Column(name = "blocks_hire", nullable = false)
    private boolean blocksHire = true;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
