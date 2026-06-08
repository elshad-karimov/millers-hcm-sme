package az.millers.hcm.organization.domain;

import java.time.LocalDate;
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
 * M142 — HRBP assignment registry (§24).
 *
 * <p>One row per (org_unit, employee, effective_from) combination.
 * Supports multiple HRBPs per unit (primary + backup via
 * {@link #backup}) and effective-dated assignments.
 *
 * <p>The workflow engine uses {@code org_unit.hrbp_id} for fast
 * single-lookup resolution; this table backs the admin UI and the
 * future multi-HRBP/workload-report surface.
 */
@Entity
@Table(name = "hr_partner", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class HrPartner {

    @Id
    private UUID id;

    /** Soft FK to {@code organization.org_unit} (not versioned). */
    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    /** Soft FK to {@code core_hr.employee} — the HRBP person. */
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** When {@code true} this is the backup HRBP for the unit. */
    @Column(name = "is_backup", nullable = false)
    private boolean backup;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
