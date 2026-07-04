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

/** HCM_12 M400 — one continuous-feedback note (PRD §22.1). */
@Entity
@Table(name = "continuous_feedback", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class ContinuousFeedback {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "author_employee_id")
    private UUID authorEmployeeId;

    /** PRAISE | IMPROVEMENT | NOTE | REQUEST */
    @Column(nullable = false, length = 20)
    private String kind = "PRAISE";

    /** EMPLOYEE_VISIBLE | MANAGER_PRIVATE (hidden from the employee). */
    @Column(nullable = false, length = 20)
    private String visibility = "EMPLOYEE_VISIBLE";

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(length = 300)
    private String tags;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
