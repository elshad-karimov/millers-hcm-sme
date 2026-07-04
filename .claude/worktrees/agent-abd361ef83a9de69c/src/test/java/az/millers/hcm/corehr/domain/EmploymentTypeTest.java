package az.millers.hcm.corehr.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for {@link EmploymentType#proRataMultiplier(BigDecimal)} —
 * the function the {@code PayrollEngine} uses to scale base salary for
 * part-time / contractor / intern employees (M61 / P1-09).
 */
class EmploymentTypeTest {

    @Test
    void salariedFullTimeTypesAlwaysReturnOne() {
        // Even if HR sets fte_percent=50 on a PERMANENT employee, payroll
        // does NOT scale the salary — salaried full-time is salaried.
        assertThat(EmploymentType.PERMANENT.proRataMultiplier(new BigDecimal("50")))
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(EmploymentType.FIXED_TERM.proRataMultiplier(new BigDecimal("80")))
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(EmploymentType.PROBATIONARY.proRataMultiplier(new BigDecimal("100")))
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void proRataTypesScaleByFtePercent() {
        assertThat(EmploymentType.PART_TIME.proRataMultiplier(new BigDecimal("50")))
                .isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(EmploymentType.PART_TIME.proRataMultiplier(new BigDecimal("60")))
                .isEqualByComparingTo(new BigDecimal("0.6000"));
        assertThat(EmploymentType.CONTRACTOR.proRataMultiplier(new BigDecimal("75")))
                .isEqualByComparingTo(new BigDecimal("0.7500"));
        assertThat(EmploymentType.INTERN.proRataMultiplier(new BigDecimal("25")))
                .isEqualByComparingTo(new BigDecimal("0.2500"));
    }

    @Test
    void proRataAtHundredEqualsOne() {
        // A part-timer at 100 FTE behaves like full-time pay — useful when an
        // employee starts as PART_TIME then gets bumped to 100% without yet
        // having their employment_type updated.
        assertThat(EmploymentType.PART_TIME.proRataMultiplier(new BigDecimal("100")))
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void nullFteFallsBackToOneForAllTypes() {
        // Defensive: a missing fte_percent must not zero-out anyone's pay.
        for (EmploymentType t : EmploymentType.values()) {
            assertThat(t.proRataMultiplier(null))
                    .as("null FTE for %s should default to 1.0", t)
                    .isEqualByComparingTo(BigDecimal.ONE);
        }
    }
}
