package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * M129 — pure-static bonus payout simulator.
 *
 * <p>Given a pool size and a weighting recipe, distributes the pool
 * across employees by:
 * <ol>
 *   <li>Computing a per-employee {@code score} = weighted blend of
 *       performance (1–5), tenure (months), and base-salary,</li>
 *   <li>Normalising scores to weights that sum to 1,</li>
 *   <li>Allocating the pool by weight,</li>
 *   <li>Clamping each allocation to {@code [floor%, cap%]} of base
 *       salary, then re-distributing any leftover proportionally
 *       across the unclamped employees.</li>
 * </ol>
 *
 * <p>Mockito-free so the math gets pinned by plain JUnit (Java 25
 * class-file v69 isn't supported by Byte Buddy).
 */
public final class BonusSimulator {

    public static final int MONEY_SCALE = 2;
    public static final int RATIO_SCALE = 6;
    public static final RoundingMode ROUND = RoundingMode.HALF_UP;

    /** Re-clamp + re-distribute up to this many times. After that, leftover stays at the residual. */
    static final int MAX_REDISTRIBUTE_ROUNDS = 6;

    /** Snapshot of one employee for the simulator. */
    public record EmployeeContext(
            UUID employeeId,
            BigDecimal baseSalary,
            /** Performance rating 1..5. Null/≤0 treated as 1. */
            BigDecimal performanceRating,
            /** Tenure in months. Negative clamps to 0. */
            int tenureMonths) {}

    /**
     * Knobs HR slides on the SPA.
     *
     * @param poolTotal       absolute pool size, in the cycle's currency.
     * @param performanceWeight weight of the performance term in the score blend.
     * @param tenureWeight      weight of the tenure term.
     * @param baseWeight        weight of the base-salary term.
     * @param floorPercent      lower bound on each bonus as a % of base salary
     *                          (0 = no floor).
     * @param capPercent        upper bound on each bonus as a % of base salary
     *                          (0 = no cap).
     */
    public record SimulationConfig(
            BigDecimal poolTotal,
            BigDecimal performanceWeight,
            BigDecimal tenureWeight,
            BigDecimal baseWeight,
            BigDecimal floorPercent,
            BigDecimal capPercent) {}

    /** One row of the simulation outcome. */
    public record EmployeeAllocation(
            UUID employeeId,
            BigDecimal baseSalary,
            BigDecimal score,
            BigDecimal weight,
            BigDecimal allocatedBonus,
            /** Allocated bonus as % of base salary, 2 dp. */
            BigDecimal percentOfBase,
            /** True iff floor/cap clamped this employee. */
            boolean clamped) {}

    /** Aggregate roll-up for the SPA summary row. */
    public record SimulationResult(
            BigDecimal poolTotal,
            BigDecimal totalAllocated,
            BigDecimal residual,
            int eligibleCount,
            int clampedCount,
            List<EmployeeAllocation> allocations) {}

    private BonusSimulator() {}

    /**
     * Run the simulation. Defensive on every input — null pool / null
     * weights / empty employees → an empty result with the configured
     * pool intact as the residual.
     */
    public static SimulationResult simulate(List<EmployeeContext> employees,
                                             SimulationConfig config) {
        BigDecimal pool = config == null || config.poolTotal() == null
                ? BigDecimal.ZERO
                : config.poolTotal().setScale(MONEY_SCALE, ROUND);
        if (employees == null || employees.isEmpty() || pool.signum() <= 0) {
            return new SimulationResult(pool, BigDecimal.ZERO.setScale(MONEY_SCALE),
                    pool, 0, 0, List.of());
        }

        BigDecimal perfW = nzWeight(config.performanceWeight());
        BigDecimal tenW = nzWeight(config.tenureWeight());
        BigDecimal baseW = nzWeight(config.baseWeight());
        BigDecimal totalW = perfW.add(tenW).add(baseW);
        // All weights zero → flat split: every score = 1, pool divided
        // evenly. Skip the weighted blend entirely so performance/tenure
        // can't sneak in through a fallback weight.
        boolean flatSplit = totalW.signum() <= 0;

        // Compute raw scores.
        List<BigDecimal> scores = new ArrayList<>(employees.size());
        BigDecimal maxPerf = BigDecimal.ONE;
        BigDecimal maxTen = BigDecimal.ONE;
        BigDecimal maxBase = BigDecimal.ONE;
        for (EmployeeContext e : employees) {
            BigDecimal perf = e.performanceRating() == null || e.performanceRating().signum() <= 0
                    ? BigDecimal.ONE : e.performanceRating();
            BigDecimal ten = BigDecimal.valueOf(Math.max(0, e.tenureMonths()));
            BigDecimal base = e.baseSalary() == null ? BigDecimal.ZERO : e.baseSalary();
            if (perf.compareTo(maxPerf) > 0) maxPerf = perf;
            if (ten.compareTo(maxTen)   > 0) maxTen = ten;
            if (base.compareTo(maxBase) > 0) maxBase = base;
        }
        // Normalise each term to [0..1] against the cohort max so weights
        // mix at the same scale regardless of currency or rating units.
        for (EmployeeContext e : employees) {
            if (flatSplit) {
                scores.add(BigDecimal.ONE.setScale(RATIO_SCALE, ROUND));
                continue;
            }
            BigDecimal perf = e.performanceRating() == null || e.performanceRating().signum() <= 0
                    ? BigDecimal.ONE : e.performanceRating();
            BigDecimal ten = BigDecimal.valueOf(Math.max(0, e.tenureMonths()));
            BigDecimal base = e.baseSalary() == null ? BigDecimal.ZERO : e.baseSalary();
            BigDecimal perfNorm = maxPerf.signum() > 0
                    ? perf.divide(maxPerf, RATIO_SCALE, ROUND) : BigDecimal.ZERO;
            BigDecimal tenNorm = maxTen.signum() > 0
                    ? ten.divide(maxTen, RATIO_SCALE, ROUND) : BigDecimal.ZERO;
            BigDecimal baseNorm = maxBase.signum() > 0
                    ? base.divide(maxBase, RATIO_SCALE, ROUND) : BigDecimal.ZERO;
            BigDecimal score = perfNorm.multiply(perfW)
                    .add(tenNorm.multiply(tenW))
                    .add(baseNorm.multiply(baseW))
                    .setScale(RATIO_SCALE, ROUND);
            scores.add(score);
        }
        BigDecimal totalScore = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalScore.signum() <= 0) {
            // Every score is zero — distribute pool flat.
            BigDecimal n = BigDecimal.valueOf(employees.size());
            BigDecimal each = pool.divide(n, MONEY_SCALE, ROUND);
            for (int i = 0; i < employees.size(); i++) {
                scores.set(i, BigDecimal.ONE);
            }
            totalScore = BigDecimal.valueOf(employees.size());
            // Fall through to allocation with flat scores.
            // (each is recomputed below from weights.)
            // We intentionally don't return early — the floor/cap pass
            // still applies.
            // Note: `each` is informational only.
            assert each.signum() >= 0;
        }

        // Initial allocation by weight.
        List<BigDecimal> rawAllocs = new ArrayList<>(employees.size());
        for (BigDecimal s : scores) {
            BigDecimal w = totalScore.signum() > 0
                    ? s.divide(totalScore, RATIO_SCALE, ROUND)
                    : BigDecimal.ZERO;
            BigDecimal alloc = pool.multiply(w).setScale(MONEY_SCALE, ROUND);
            rawAllocs.add(alloc);
        }

        // Floor/cap clamp + re-distribute leftover up to MAX rounds.
        boolean[] frozen = new boolean[employees.size()];
        BigDecimal floorPct = config.floorPercent() == null ? BigDecimal.ZERO : config.floorPercent();
        BigDecimal capPct = config.capPercent() == null ? BigDecimal.ZERO : config.capPercent();
        BigDecimal[] finalAllocs = rawAllocs.toArray(new BigDecimal[0]);

        for (int round = 0; round < MAX_REDISTRIBUTE_ROUNDS; round++) {
            BigDecimal leftover = BigDecimal.ZERO;
            BigDecimal unfrozenWeight = BigDecimal.ZERO;
            BigDecimal newlyFrozen = BigDecimal.ZERO;
            for (int i = 0; i < employees.size(); i++) {
                if (frozen[i]) continue;
                BigDecimal base = employees.get(i).baseSalary() == null
                        ? BigDecimal.ZERO : employees.get(i).baseSalary();
                BigDecimal floor = floorPct.signum() > 0
                        ? base.multiply(floorPct).divide(BigDecimal.valueOf(100), MONEY_SCALE, ROUND)
                        : BigDecimal.ZERO;
                BigDecimal cap = capPct.signum() > 0
                        ? base.multiply(capPct).divide(BigDecimal.valueOf(100), MONEY_SCALE, ROUND)
                        : null;
                BigDecimal a = finalAllocs[i];
                if (cap != null && a.compareTo(cap) > 0) {
                    leftover = leftover.add(a.subtract(cap));
                    finalAllocs[i] = cap;
                    frozen[i] = true;
                    newlyFrozen = newlyFrozen.add(BigDecimal.ONE);
                } else if (a.compareTo(floor) < 0) {
                    leftover = leftover.subtract(floor.subtract(a));
                    finalAllocs[i] = floor;
                    frozen[i] = true;
                    newlyFrozen = newlyFrozen.add(BigDecimal.ONE);
                } else {
                    unfrozenWeight = unfrozenWeight.add(scores.get(i));
                }
            }
            if (newlyFrozen.signum() == 0) break; // stable
            if (unfrozenWeight.signum() == 0) break; // can't redistribute further
            // Re-distribute leftover proportionally across remaining
            // unfrozen employees by their score weight.
            for (int i = 0; i < employees.size(); i++) {
                if (frozen[i]) continue;
                BigDecimal share = scores.get(i)
                        .divide(unfrozenWeight, RATIO_SCALE, ROUND)
                        .multiply(leftover)
                        .setScale(MONEY_SCALE, ROUND);
                finalAllocs[i] = finalAllocs[i].add(share);
            }
        }

        // Build result rows.
        List<EmployeeAllocation> out = new ArrayList<>(employees.size());
        BigDecimal totalAllocated = BigDecimal.ZERO;
        int clampedCount = 0;
        for (int i = 0; i < employees.size(); i++) {
            EmployeeContext e = employees.get(i);
            BigDecimal base = e.baseSalary() == null ? BigDecimal.ZERO : e.baseSalary();
            BigDecimal alloc = finalAllocs[i].setScale(MONEY_SCALE, ROUND);
            BigDecimal weight = totalScore.signum() > 0
                    ? scores.get(i).divide(totalScore, RATIO_SCALE, ROUND)
                    : BigDecimal.ZERO;
            BigDecimal pct = base.signum() > 0
                    ? alloc.multiply(BigDecimal.valueOf(100))
                            .divide(base, MONEY_SCALE, ROUND)
                    : BigDecimal.ZERO;
            if (frozen[i]) clampedCount++;
            totalAllocated = totalAllocated.add(alloc);
            out.add(new EmployeeAllocation(
                    e.employeeId(), base, scores.get(i), weight,
                    alloc, pct, frozen[i]));
        }
        BigDecimal residual = pool.subtract(totalAllocated).setScale(MONEY_SCALE, ROUND);
        return new SimulationResult(pool, totalAllocated, residual,
                employees.size(), clampedCount, out);
    }

    private static BigDecimal nzWeight(BigDecimal w) {
        if (w == null || w.signum() < 0) return BigDecimal.ZERO;
        return w;
    }
}
