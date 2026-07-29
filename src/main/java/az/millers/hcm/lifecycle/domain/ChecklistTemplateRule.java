package az.millers.hcm.lifecycle.domain;

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
import org.hibernate.annotations.TenantId;

/**
 * One match rule for a {@link ChecklistTemplate} (M298 — Onboarding Phase A.1).
 *
 * <p>Every non-null column is an AND-ed filter evaluated against the new hire's
 * {@code Employee} attributes; a rule with all columns null matches everyone
 * (a catch-all default). A template matches an employee if <em>any</em> of its
 * active rules match. Selection specificity is the count of non-null columns on
 * the matching rule — see {@code ChecklistTemplateMatcher}.
 */
@Entity
@Table(name = "checklist_template_rule", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ChecklistTemplateRule {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    /** Matches {@code Employee.departmentName} (case-insensitive). */
    @Column(name = "department_name", length = 160)
    private String departmentName;

    /** Matches {@code Employee.positionId}. */
    @Column(name = "position_id")
    private UUID positionId;

    /** Matches {@code Employee.orgUnitId}. */
    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    /** Matches {@code Employee.workLocationId} (branch / site). */
    @Column(name = "work_location_id")
    private UUID workLocationId;

    /** Matches {@code Employee.employmentType} enum name. */
    @Column(name = "employment_type", length = 20)
    private String employmentType;

    /** Matches {@code Employee.employeeCategory} (case-insensitive). */
    @Column(name = "employee_category", length = 60)
    private String employeeCategory;

    /** Matches {@code Employee.nationality} (ISO 3166-1 alpha-2). */
    @Column(name = "nationality", length = 2)
    private String nationality;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    /** Number of non-null filter columns — higher = more specific match. */
    public int specificity() {
        int n = 0;
        if (departmentName != null) n++;
        if (positionId != null) n++;
        if (orgUnitId != null) n++;
        if (workLocationId != null) n++;
        if (employmentType != null) n++;
        if (employeeCategory != null) n++;
        if (nationality != null) n++;
        return n;
    }
}
