package az.millers.hcm.performance.domain;

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

/** HCM_15 M409 — employee-editable career interest (PRD 15 §6 / 17 §5). */
@Entity
@Table(name = "career_interest", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class CareerInterest {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "target_role", nullable = false, length = 200)
    private String targetRole;

    @Column(name = "target_department", length = 200)
    private String targetDepartment;

    @Column(name = "target_location", length = 200)
    private String targetLocation;

    /** UNDER_1_YEAR | ONE_TO_TWO_YEARS | TWO_TO_FIVE_YEARS | LONG_TERM */
    @Column(length = 20)
    private String timeline;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
