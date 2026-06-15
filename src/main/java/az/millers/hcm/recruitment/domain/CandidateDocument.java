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

/**
 * M292 — Recruitment PRD §12: one document checklist item on a
 * {@link Candidate}. The uploaded file (if any) lives in MinIO via the
 * M16 attachment registry, scoped to this row's id.
 */
@Entity
@Table(name = "candidate_document", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class CandidateDocument {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "document_type_id", nullable = false)
    private UUID documentTypeId;

    @Column(nullable = false)
    private boolean required = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(length = 500)
    private String notes;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "verified_by", length = 120)
    private String verifiedBy;

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
}
