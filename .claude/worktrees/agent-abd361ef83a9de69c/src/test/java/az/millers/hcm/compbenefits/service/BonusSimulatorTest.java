package az.millers.hcm.compbenefits.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.compbenefits.service.BonusSimulator.EmployeeAllocation;
import az.millers.hcm.compbenefits.service.BonusSimulator.EmployeeContext;
import az.millers.hcm.compbenefits.service.BonusSimulator.SimulationConfig;
import az.millers.hcm.compbenefits.service.BonusSimulator.SimulationResult;

/**
 * M129 — pins the bonus simulator math. The contracts production code
 * leans on:
 * <ul>
 *   <li>empty inputs → empty result with the pool intact as residual,</li>
 *   <li>higher score gets larger allocation,</li>
 *   <li>cap clamps the high earner and redistributes,</li>
 *   <li>floor lifts the low earner and redistributes (or absorbs),</li>
 *   <li>per-employee % of base is computed correctly.</li>
 * </ul>
 */
class BonusSimulatorTest {

    private static EmployeeContext emp(BigDecimal base, BigDecimal rating, int tenure) {
        return new EmployeeContext(UUID.randomUUID(), base, rating, tenure);
    }

    private static SimulationConfig cfg(BigDecimal pool, BigDecimal perf, BigDecimal ten,
                                         BigDecimal base, BigDecimal floor, BigDecimal cap) {
        return new SimulationConfig(pool, perf, ten, base, floor, cap);
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static BigDecimal bd(int v)    { return new BigDecimal(v); }

    // ── empty / degenerate inputs ─────────────────────────────────────────

    @Test
    void emptyEmployeesYieldsEmptyResult() {
        SimulationResult r = BonusSimulator.simulate(
                List.of(),
                cfg(bd(1000), bd(1), bd(0), bd(0), bd(0), bd(0)));
        assertThat(r.allocations()).isEmpty();
        assertThat(r.totalAllocated()).isEqualByComparingTo("0.00");
        assertThat(r.residual()).isEqualByComparingTo("1000.00");
    }

    @Test
    void zeroPoolYieldsZeroAllocations() {
        SimulationResult r = BonusSimulator.simulate(
                List.of(emp(bd(50000), bd(4), 24)),
                cfg(bd(0), bd(1), bd(0), bd(0), bd(0), bd(0)));
        assertThat(r.allocations()).isEmpty();
    }

    @Test
    void allZeroWeightsFallsBackToFlatSplit() {
        SimulationResult r = BonusSimulator.simulate(
                List.of(
                    emp(bd(50000), bd(5), 24),
                    emp(bd(50000), bd(1), 24)),
                cfg(bd(1000), bd(0), bd(0), bd(0), bd(0), bd(0)));
        // Both employees get the same allocation when weights are flat
        // and bases are identical.
        assertThat(r.allocations().get(0).allocatedBonus())
                .isEqualByComparingTo(r.allocations().get(1).allocatedBonus());
    }

    // ── ordering ───────────────────────────────────────────────────────────

    @Test
    void higherRatingGetsMoreWhenPerformanceWeighs() {
        EmployeeContext low = emp(bd(50000), bd(2), 24);
        EmployeeContext high = emp(bd(50000), bd(5), 24);
        SimulationResult r = BonusSimulator.simulate(
                List.of(low, high),
                cfg(bd(1000), bd(1), bd(0), bd(0), bd(0), bd(0)));
        EmployeeAllocation lowA = find(r, low.employeeId());
        EmployeeAllocation highA = find(r, high.employeeId());
        assertThat(highA.allocatedBonus()).isGreaterThan(lowA.allocatedBonus());
    }

    @Test
    void longerTenureGetsMoreWhenTenureWeighs() {
        EmployeeContext junior = emp(bd(50000), bd(3), 6);
        EmployeeContext senior = emp(bd(50000), bd(3), 120);
        SimulationResult r = BonusSimulator.simulate(
                List.of(junior, senior),
                cfg(bd(1000), bd(0), bd(1), bd(0), bd(0), bd(0)));
        EmployeeAllocation jA = find(r, junior.employeeId());
        EmployeeAllocation sA = find(r, senior.employeeId());
        assertThat(sA.allocatedBonus()).isGreaterThan(jA.allocatedBonus());
    }

    @Test
    void higherBaseGetsMoreWhenBaseWeighs() {
        EmployeeContext jr = emp(bd(30000), bd(3), 24);
        EmployeeContext sr = emp(bd(100000), bd(3), 24);
        SimulationResult r = BonusSimulator.simulate(
                List.of(jr, sr),
                cfg(bd(1000), bd(0), bd(0), bd(1), bd(0), bd(0)));
        assertThat(find(r, sr.employeeId()).allocatedBonus())
                .isGreaterThan(find(r, jr.employeeId()).allocatedBonus());
    }

    // ── cap clamp + redistribute ──────────────────────────────────────────

    @Test
    void capClampsHighAllocationAndRedistributes() {
        EmployeeContext star = emp(bd(50000), bd(5), 24);
        EmployeeContext steady = emp(bd(50000), bd(3), 24);
        // Pool 10,000. Cap 10% → max 5000 per employee.
        SimulationResult r = BonusSimulator.simulate(
                List.of(star, steady),
                cfg(bd(10000), bd(1), bd(0), bd(0), bd(0), bd(10)));
        EmployeeAllocation starA = find(r, star.employeeId());
        EmployeeAllocation steadyA = find(r, steady.employeeId());
        // Star gets capped at 10% of 50000 = 5000.
        assertThat(starA.allocatedBonus()).isEqualByComparingTo("5000.00");
        assertThat(starA.clamped()).isTrue();
        // Steady absorbs the leftover; total ≈ pool.
        assertThat(starA.allocatedBonus().add(steadyA.allocatedBonus()))
                .isCloseTo(bd(10000), org.assertj.core.api.Assertions.within(bd("1")));
    }

    // ── floor lift + redistribute ─────────────────────────────────────────

    @Test
    void floorLiftsLowAllocation() {
        // Two employees, very lopsided score → one would get tiny bonus.
        // Floor 5% lifts the small one.
        EmployeeContext small = emp(bd(50000), bd(1), 12);
        EmployeeContext big = emp(bd(50000), bd(5), 60);
        SimulationResult r = BonusSimulator.simulate(
                List.of(small, big),
                cfg(bd(10000), bd("0.5"), bd("0.5"), bd(0),
                        bd(5), bd(0)));
        EmployeeAllocation smallA = find(r, small.employeeId());
        // Floor 5% of 50000 = 2500.
        assertThat(smallA.allocatedBonus()).isGreaterThanOrEqualTo(bd("2500"));
        assertThat(smallA.clamped()).isTrue();
    }

    // ── percent-of-base ───────────────────────────────────────────────────

    @Test
    void percentOfBaseIsAllocOverBaseTimes100() {
        EmployeeContext e = emp(bd(50000), bd(3), 24);
        SimulationResult r = BonusSimulator.simulate(
                List.of(e),
                cfg(bd(5000), bd(1), bd(0), bd(0), bd(0), bd(0)));
        EmployeeAllocation a = r.allocations().get(0);
        // Single employee gets the whole pool: 5000 / 50000 = 10%.
        assertThat(a.percentOfBase()).isEqualByComparingTo("10.00");
    }

    @Test
    void totalAllocatedAndResidualSumToPool() {
        List<EmployeeContext> emps = List.of(
                emp(bd(50000), bd(3), 24),
                emp(bd(60000), bd(4), 36),
                emp(bd(75000), bd(5), 48));
        SimulationResult r = BonusSimulator.simulate(
                emps,
                cfg(bd(10000), bd("0.5"), bd("0.2"), bd("0.3"), bd(0), bd(0)));
        assertThat(r.totalAllocated().add(r.residual()))
                .isEqualByComparingTo(r.poolTotal());
    }

    @Test
    void nullPoolYieldsZero() {
        SimulationResult r = BonusSimulator.simulate(
                List.of(emp(bd(50000), bd(3), 24)),
                cfg(null, bd(1), bd(0), bd(0), bd(0), bd(0)));
        assertThat(r.allocations()).isEmpty();
        assertThat(r.poolTotal()).isEqualByComparingTo("0.00");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static EmployeeAllocation find(SimulationResult r, UUID id) {
        return r.allocations().stream()
                .filter(a -> a.employeeId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
