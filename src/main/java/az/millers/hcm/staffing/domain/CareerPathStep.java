package az.millers.hcm.staffing.domain;

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
 * HCM_16 M416 — step in a career path (PRD §16.6).
 * from_position → to_position with required skills, certs, experience, courses.
 */
@Entity
@Table(name = "career_path_step", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class CareerPathStep {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "path_id", nullable = false)
    private UUID pathId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "from_position_id")
    private UUID fromPositionId;

    @Column(name = "to_position_id", nullable = false)
    private UUID toPositionId;

    @Column(name = "required_skills", length = 1000)
    private String requiredSkills;

    @Column(name = "required_certifications", length = 500)
    private String requiredCertifications;

    @Column(name = "required_experience_years")
    private Integer requiredExperienceYears;

    @Column(name = "required_courses", length = 500)
    private String requiredCourses;

    @Column(name = "typical_tenure_months")
    private Integer typicalTenureMonths;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
