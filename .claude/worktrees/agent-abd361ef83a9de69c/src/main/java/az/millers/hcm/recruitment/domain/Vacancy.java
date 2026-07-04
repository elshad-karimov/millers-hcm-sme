package az.millers.hcm.recruitment.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vacancy", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Vacancy {

    @Id
    private UUID id;

    @Column(name = "vacancy_no", nullable = false, unique = true)
    private String vacancyNo;

    @Column(nullable = false)
    private String title;

    @Column(name = "position_id")
    private UUID positionId;

    private String department;
    private String location;

    @Column(nullable = false)
    private int openings;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String requirements;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "hiring_manager_id")
    private UUID hiringManagerId;

    @Column(name = "recruiter_id")
    private UUID recruiterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacancyStatus status;

    // ── M274 / Recruitment PRD §4 — requisition fields ──────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "requisition_type", nullable = false, length = 32)
    private RequisitionType requisitionType = RequisitionType.NEW_HEADCOUNT;

    @Enumerated(EnumType.STRING)
    @Column(name = "hiring_reason", length = 64)
    private HiringReason hiringReason;

    @Column(name = "target_start_date")
    private LocalDate targetStartDate;

    @Column(name = "cost_centre", length = 64)
    private String costCentre;

    /** Mirrors the position's employment type so the offer can default it. */
    @Column(name = "employment_type", length = 32)
    private String employmentType;

    /** For REPLACEMENT requisitions — the departing employee being backfilled. */
    @Column(name = "replaced_employee_id")
    private UUID replacedEmployeeId;

    /** M275 — the approval WorkflowInstance driving DRAFT → APPROVED. */
    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    /**
     * M277 — Recruitment PRD §41: confidential requisitions are visible
     * only to the named recruiter, the named hiring manager, and
     * unrestricted-scope users (HR_ADMIN / SYSTEM_ADMIN).
     */
    @Column(nullable = false)
    private boolean confidential;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
