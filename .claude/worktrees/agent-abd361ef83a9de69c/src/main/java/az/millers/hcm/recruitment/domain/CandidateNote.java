package az.millers.hcm.recruitment.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only CRM note on a candidate (M87). Distinct from
 * application-level evaluations — these are cross-vacancy touches like
 * "called for a chat", "met at conference", "responded to outreach".
 */
@Entity
@Table(name = "candidate_note", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class CandidateNote {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateNoteKind kind = CandidateNoteKind.NOTE;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** Optional date the touch happened — defaults to today when null. */
    @Column(name = "contact_date")
    private LocalDate contactDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
