package az.millers.hcm.recruitment.domain;

import java.math.BigDecimal;
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

/** M291 — one rated skill on a {@link Candidate} (PRD §11). */
@Entity
@Table(name = "candidate_skill", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class CandidateSkill {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private SkillProficiency proficiency;

    @Column(name = "years_experience", precision = 4, scale = 1)
    private BigDecimal yearsExperience;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
