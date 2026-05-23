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

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (awardedAt == null) awardedAt = OffsetDateTime.now();
    }
}
