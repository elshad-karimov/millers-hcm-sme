package az.millers.hcm.corehr.domain;

import java.math.BigDecimal;

/**
 * Employment type on the employee aggregate (PRD §8.1, Phase 1 / P1-09).
 *
 * <p>Lives on the employee, not on {@code staffing.position}, because an employee
 * may exist without a linked position — without this field the payroll engine
 * cannot resolve pro-rata multipliers for part-timers and interns.
 *
 * <p>Pro-rata behaviour is driven by {@link #isFullTimeByDefault()}: if {@code false}
 * the {@code PayrollEngine} applies the employee's {@code fte_percent} factor to
 * the base salary. {@code PERMANENT} and {@code FIXED_TERM} stay at 100% even
 * if {@code fte_percent} differs (so HR can record FTE for analytics without
 * accidentally docking salaried staff).
 */
public enum EmploymentType {

    /** Open-ended salaried role. Full-time by default. */
    PERMANENT(true),

    /** Salaried role with a contractual end date. Full-time by default. */
    FIXED_TERM(true),

    /** Pro-rata salaried by {@code fte_percent}. */
    PART_TIME(false),

    /** Salaried during probation period; transitions to PERMANENT on confirmation. */
    PROBATIONARY(true),

    /** Independent contractor — pro-rata by fte / no statutory contributions. */
    CONTRACTOR(false),

    /** Trainee / intern — pro-rata by fte. */
    INTERN(false);

    private final boolean fullTimeByDefault;

    EmploymentType(boolean fullTimeByDefault) {
        this.fullTimeByDefault = fullTimeByDefault;
    }

    public boolean isFullTimeByDefault() {
        return fullTimeByDefault;
    }

    /**
     * Multiplier to apply to base salary at payroll calculation time.
     *
     * @param ftePercent the employee's FTE percentage (100.00 = full-time)
     * @return 1.00 for salaried full-time types; fte/100 for pro-rata types
     */
    public BigDecimal proRataMultiplier(BigDecimal ftePercent) {
        if (fullTimeByDefault) {
            return BigDecimal.ONE;
        }
        if (ftePercent == null) {
            return BigDecimal.ONE;
        }
        return ftePercent.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
    }
}
