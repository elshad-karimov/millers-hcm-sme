package az.millers.hcm.recruitment.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Free-text tag on a candidate (M87). One row per tag; the DB enforces
 * uniqueness on {@code (candidate_id, lower(tag))} so the same tag can't
 * be added twice with different casing.
 */
@Entity
@Table(name = "candidate_tag", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class CandidateTag {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false, length = 60)
    private String tag;

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
