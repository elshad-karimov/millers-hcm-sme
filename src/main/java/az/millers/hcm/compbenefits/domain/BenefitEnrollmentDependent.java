package az.millers.hcm.compbenefits.domain;

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
 * HCM_11 M376 — links a {@link BenefitEnrollment} to a covered dependent
 * ({@code core_hr.employee_dependent}). Replaces M108's bare dependents_covered count.
 */
@Entity
@Table(name = "benefit_enrollment_dependent", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitEnrollmentDependent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "dependent_id", nullable = false)
    private UUID dependentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
