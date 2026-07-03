package az.millers.hcm.performance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HCM_12 M389 — review template (PRD §5.2): which sections a review form has and each
 * scoring section's weight in the §18.2 overall score. Applicability filters are AND-ed;
 * null = any (same pattern as benefit eligibility rules).
 */
@Entity
@Table(name = "perf_review_template", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class PerfReviewTemplate {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "template_code", nullable = false, length = 40)
    private String templateCode;

    @Column(name = "template_name", nullable = false, length = 160)
    private String templateName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "grade_id")
    private UUID gradeId;

    @Column(name = "employee_type", length = 40)
    private String employeeType;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

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
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
