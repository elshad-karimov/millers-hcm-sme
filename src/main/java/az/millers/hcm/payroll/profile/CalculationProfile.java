package az.millers.hcm.payroll.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * How one contract / work regime is priced.
 *
 * <p>The company has no universal salary formula. An employee is assigned a
 * profile and payroll dispatches to the matching engine — two employees with
 * identical timesheets and identical base salaries are paid different amounts
 * if their profiles differ, and that is correct.
 *
 * <p>The distinction that must never be collapsed:
 * <pre>
 * ONSHORE_RANDOM_OFFSHORE : offshoreHours x hourlyRate x 1.75   (hour-driven)
 * OFFSHORE_ROTATION       : baseSalary x 1.75                   (month-driven)
 * </pre>
 *
 * <p>Two fields are deliberately nullable and make the engine refuse rather
 * than guess: {@link #excessMultiplier} (BLOCKERS Q2) and
 * {@link #excessHoursIncludeNight} (BLOCKERS Q1).
 */
@Entity
@Table(name = "calculation_profile", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class CalculationProfile {

    /** No offshore component. */
    public static final String OFFSHORE_NONE = "NONE";
    /** (offshoreHours + offshoreNightHours) x hourlyRate x multiplier. */
    public static final String OFFSHORE_HOURLY = "HOURLY";
    /** baseSalary x multiplier, regardless of hours worked. */
    public static final String OFFSHORE_MONTHLY_BASE = "MONTHLY_BASE";
    /** hourlyRate x (norm - onshoreHours - sickHours) x multiplier. */
    public static final String OFFSHORE_DERIVED_FROM_NORM = "DERIVED_FROM_NORM";

    public static final String EXCESS_NONE = "NONE";
    public static final String EXCESS_MONTHLY = "MONTHLY";
    public static final String EXCESS_BALANCING_PERIOD = "BALANCING_PERIOD";

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "offshore_salary_mode", nullable = false, length = 24)
    private String offshoreSalaryMode = OFFSHORE_NONE;

    @Column(name = "offshore_multiplier", precision = 8, scale = 4)
    private BigDecimal offshoreMultiplier;

    @Column(name = "excess_method", nullable = false, length = 24)
    private String excessMethod = EXCESS_NONE;

    /** Null for rotation on purpose — 3.50 vs 2.75 is unresolved (BLOCKERS Q2). */
    @Column(name = "excess_multiplier", precision = 8, scale = 4)
    private BigDecimal excessMultiplier;

    /**
     * Whether night hours are a separate addend rather than a subset of the
     * offshore / quayside figure. Null on purpose — see BLOCKERS Q1.
     *
     * <p>Null is handled differently on the two paths it governs. Earnings fall
     * back to treating night as a subset, which is slice 3's behaviour pinned to
     * the cent against the January workbook, and warn that they did. Excess
     * refuses: there is no validated precedent for deriving it from hours.
     */
    @Column(name = "night_hours_separate_from_base")
    private Boolean nightHoursSeparateFromBase;

    @Column(name = "balancing_scheme_code", length = 40)
    private String balancingSchemeCode;

    /**
     * Comma-separated category codes summed into the balancing accumulator.
     * Seeded to mirror the monthly excess sum; BLOCKERS Q6.1 asks whether
     * quayside and holiday hours belong in it too.
     */
    @Column(name = "accumulator_categories", columnDefinition = "text")
    private String accumulatorCategories;

    @Column(name = "derived_offshore_deducts_sick", nullable = false)
    private boolean derivedOffshoreDeductsSick = true;

    @Column(name = "planned_daily_hours", precision = 5, scale = 2)
    private BigDecimal plannedDailyHours;

    @Column(name = "work_pattern", length = 40)
    private String workPattern;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean settlesExcessOverBalancingPeriod() {
        return EXCESS_BALANCING_PERIOD.equals(excessMethod);
    }

    public boolean settlesExcessMonthly() {
        return EXCESS_MONTHLY.equals(excessMethod);
    }

    /**
     * True when night hours must be added to the 1.75 / 1.60 base. Unanswered
     * resolves to false — the reading slice 3 validated against the workbook.
     */
    public boolean addsNightHoursToBase() {
        return Boolean.TRUE.equals(nightHoursSeparateFromBase);
    }

    public boolean nightTreatmentUnconfirmed() {
        return nightHoursSeparateFromBase == null;
    }

    /**
     * The categories the accumulator sums, night included only when night hours
     * are known to be extra rather than a re-classification of hours already
     * counted. Empty when nothing is configured — the caller must not guess.
     */
    public java.util.List<String> accumulatorCategoryCodes() {
        if (accumulatorCategories == null || accumulatorCategories.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> codes = new java.util.ArrayList<>(
                java.util.Arrays.stream(accumulatorCategories.split(","))
                        .map(String::trim)
                        .filter(c -> !c.isEmpty())
                        .toList());
        if (addsNightHoursToBase()) {
            for (String night : java.util.List.of("OFFSHORE_NIGHT_HOURS", "QUAYSIDE_NIGHT_HOURS")) {
                if (!codes.contains(night)) codes.add(night);
            }
        }
        return java.util.List.copyOf(codes);
    }
}
