package az.millers.hcm.learning.domain;

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

/**
 * M419: Skill verification request — employee submits, manager/HR approves.
 */
@Entity
@Table(name = "skill_verification_request", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class SkillVerificationRequest {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "requested_level", nullable = false)
    private int requestedLevel;

    @Column(name = "evidence_notes", columnDefinition = "text")
    private String evidenceNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "verified_by_employee_id")
    private UUID verifiedByEmployeeId;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "verification_notes", columnDefinition = "text")
    private String verificationNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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

    public enum VerificationStatus {
        PENDING, APPROVED, REJECTED
    }
}
