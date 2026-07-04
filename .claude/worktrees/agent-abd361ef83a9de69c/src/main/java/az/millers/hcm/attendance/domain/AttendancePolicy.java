package az.millers.hcm.attendance.domain;

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
 * Configurable attendance rules for an employee group (PRD §2, §25, §26).
 *
 * <p>Policy resolution order (most-specific wins):
 * department × employment_type → department only → type only → catch-all (all NULLs).
 *
 * <p>Resolved blocker formulas (2026-06-26):
 * <ul>
 *   <li>Late/early per-minute deduction = monthlySalary / absenceDailyDivisor / (hoursPerDay × 60)</li>
 *   <li>Absence daily deduction         = monthlySalary / absenceDailyDivisor</li>
 *   <li>Correction approval             = manager always + HR when periodLocked / absenceChanged / OT-delta > 30</li>
 * </ul>
 */
@Entity
@Table(name = "attendance_policy", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class AttendancePolicy {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    // ── Applicability ────────────────────────────────────────────────────

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "employment_type", length = 50)
    private String employmentType;

    // ── Grace periods ────────────────────────────────────────────────────

    @Column(name = "clock_in_grace_minutes", nullable = false)
    private int clockInGraceMinutes = 10;

    @Column(name = "clock_out_grace_minutes", nullable = false)
    private int clockOutGraceMinutes = 0;

    // ── Late rules (BLOCKER 1) ───────────────────────────────────────────

    @Column(name = "late_deduction_enabled", nullable = false)
    private boolean lateDeductionEnabled = false;

    @Column(name = "late_max_before_half_day_minutes", nullable = false)
    private int lateMaxBeforeHalfDayMinutes = 120;

    @Column(name = "late_monthly_threshold_minutes", nullable = false)
    private int lateMonthlyThresholdMinutes = 0;

    // ── Early leave rules (BLOCKER 3) ────────────────────────────────────

    @Column(name = "early_leave_deduction_enabled", nullable = false)
    private boolean earlyLeaveDeductionEnabled = false;

    @Column(name = "early_leave_treatment", nullable = false, length = 30)
    private String earlyLeaveTreatment = "SALARY_DEDUCTION";

    @Column(name = "early_leave_tolerance_minutes", nullable = false)
    private int earlyLeaveToleranceMinutes = 0;

    // ── Absence rules (BLOCKER 2) ─────────────────────────────────────────

    @Column(name = "absence_deduction_enabled", nullable = false)
    private boolean absenceDeductionEnabled = true;

    @Column(name = "absence_daily_divisor", nullable = false)
    private int absenceDailyDivisor = 30;

    @Column(name = "hours_per_day", nullable = false)
    private int hoursPerDay = 8;

    @Column(name = "unauthorized_absence_treatment", nullable = false, length = 30)
    private String unauthorizedAbsenceTreatment = "UNPAID";

    // ── OT rules ─────────────────────────────────────────────────────────

    @Column(name = "overtime_requires_preapproval", nullable = false)
    private boolean overtimeRequiresPreapproval = false;

    @Column(name = "overtime_dept_head_threshold_minutes", nullable = false)
    private int overtimeDeptHeadThresholdMinutes = 120;

    @Column(name = "comp_off_enabled", nullable = false)
    private boolean compOffEnabled = false;

    // ── Break rules ───────────────────────────────────────────────────────

    @Column(name = "auto_break_deduction_enabled", nullable = false)
    private boolean autoBreakDeductionEnabled = false;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 60;

    @Column(name = "min_hours_for_break_deduction", nullable = false)
    private java.math.BigDecimal minHoursForBreakDeduction = new java.math.BigDecimal("6.0");

    // ── Rounding ──────────────────────────────────────────────────────────

    @Column(name = "rounding_enabled", nullable = false)
    private boolean roundingEnabled = false;

    @Column(name = "rounding_minutes", nullable = false)
    private int roundingMinutes = 15;

    @Column(name = "rounding_direction", nullable = false, length = 10)
    private String roundingDirection = "NEAREST";

    // ── Meta ──────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Per-minute deduction rate divisor (denominator: divisor × hoursPerDay × 60). */
    public int minutesPerMonth() {
        return absenceDailyDivisor * hoursPerDay * 60;
    }
}
