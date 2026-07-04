package az.millers.hcm.learning.domain;

import java.time.LocalDate;
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

@Entity
@Table(name = "employee_competency", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeCompetency {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(nullable = false)
    private int proficiency;

    @Column(nullable = false, length = 32)
    private String source;        // COURSE, MANUAL, IMPORT

    @Column(name = "source_ref")
    private UUID sourceRef;       // enrollment_id when source=COURSE

    @Column(name = "awarded_at", nullable = false)
    private OffsetDateTime awardedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    // ── M136 — Section 16 years + manager endorsement ───────────────────

    /** Years of experience on this competency. NUMERIC(4,1); 0..99.9. */
    @Column(name = "years_of_experience")
    private java.math.BigDecimal yearsOfExperience;

    /**
     * Employee id of the manager / mentor who endorsed this record.
     * Paired with {@link #endorsedAt} and {@link #endorsedLevel} via a
     * DB CHECK — all three are set together or none at all. Note is
     * optional even when endorsed.
     */
    @Column(name = "endorsed_by_employee_id")
    private UUID endorsedByEmployeeId;

    @Column(name = "endorsed_at")
    private OffsetDateTime endorsedAt;

    /** What level the endorser confirmed (1..5). Usually equals {@link #proficiency}. */
    @Column(name = "endorsed_level")
    private Integer endorsedLevel;

    @Column(name = "endorsement_note", columnDefinition = "text")
    private String endorsementNote;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (awardedAt == null) awardedAt = OffsetDateTime.now();
    }
}
