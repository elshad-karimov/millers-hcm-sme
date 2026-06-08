package az.millers.hcm.staffing.domain;

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
@Table(name = "position", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class Position {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    /**
     * M146 / §8 — optional parent position forming a position hierarchy.
     * Self-FK; null for root-level positions.
     */
    @Column(name = "parent_position_id")
    private UUID parentPositionId;

    /** Soft reference into {@code organization.org_unit}. Not a hard FK because
     *  org units are version-scoped while positions live across versions. */
    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "org_unit_label")
    private String orgUnitLabel;

    /**
     * Legacy free-text grade label (pre-M75). Still populated for backward
     * compat; new positions should prefer {@link #gradeId} for proper
     * pay-band enforcement.
     */
    private String grade;

    /** Legacy free-text job family label (pre-M75). See {@link #jobFamilyId}. */
    @Column(name = "job_family")
    private String jobFamily;

    @Column(name = "job_level")
    private String jobLevel;

    /** M75 / P2-22 — soft FK to {@link Grade}. Null = use free-text {@link #grade}. */
    @Column(name = "grade_id")
    private UUID gradeId;

    /** M75 / P2-22 — soft FK to {@link JobFamily}. Null = use free-text {@link #jobFamily}. */
    @Column(name = "job_family_id")
    private UUID jobFamilyId;

    /** M75 / P2-22 — soft FK to {@link JobFunction}. Null = no specific function recorded. */
    @Column(name = "job_function_id")
    private UUID jobFunctionId;

    @Column(name = "approved_headcount", nullable = false)
    private int approvedHeadcount;

    @Column(name = "occupied_headcount", nullable = false)
    private int occupiedHeadcount;

    @Column(name = "salary_min", precision = 14, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 14, scale = 2)
    private BigDecimal salaryMax;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "cost_centre")
    private String costCentre;

    @Column(name = "budget_code")
    private String budgetCode;

    private String location;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "vacancy_state", nullable = false)
    private VacancyState vacancyState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status;

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
