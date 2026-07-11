package az.millers.hcm.learning.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure, Spring-free math for candidate-fit ranking — rebuilt on the learning
 * module (canonical {@code learning.position_competency_requirement}) from the
 * retired {@code skills.SkillGapAnalyzer} (deleted in {@code 0d9725b}).
 *
 * <p>Kept static and dependency-free so the fit semantics are pinned by plain
 * JUnit + AssertJ (Mockito is unusable on the project's Java 25 toolchain —
 * Byte Buddy rejects class-file v69), matching the other learning analyzers.
 *
 * <p>Two input shapes the service composes:
 * <ul>
 *   <li>{@link EmployeeSkill} — one row per (employee, competency, source) from
 *       {@code learning.employee_competency},</li>
 *   <li>{@link Requirement} — one row per (position, competency) from
 *       {@code learning.position_competency_requirement}, carrying the
 *       {@code required_proficiency} (1..5) and the {@code mandatory} flag
 *       (V307).</li>
 * </ul>
 *
 * <p>The analyzer collapses multiple employee rows to "current best level per
 * competency" (max across non-expired sources — {@code valid_until} honoured
 * exactly as the original did), compares to the position requirements, and
 * produces a 0..100 fit score used to rank candidates for a vacancy.
 */
public final class PositionFitAnalyzer {

    private PositionFitAnalyzer() {}

    /** One (employee, competency) proficiency record. */
    public record EmployeeSkill(
            UUID competencyId,
            int proficiency,
            LocalDate validUntil) {}

    /** One position requirement: the level demanded and whether it's mandatory. */
    public record Requirement(
            UUID competencyId,
            int requiredLevel,
            boolean mandatory) {}

    /** Severity of a single (current vs required) gap. */
    public enum Severity { NONE, MINOR, MAJOR, BLOCKER }

    /** One evaluated requirement for an employee. */
    public record Gap(
            UUID competencyId,
            int requiredLevel,
            /** 0 when the employee has no (non-expired) record for this competency. */
            int currentLevel,
            boolean mandatory,
            Severity severity) {}

    /**
     * Collapse multiple {@code learning.employee_competency} rows into "current
     * level per competency" by taking the MAX across non-expired sources. A row
     * whose {@code validUntil} is before {@code today} is treated as expired and
     * ignored (mirrors the retired analyzer).
     */
    public static Map<UUID, Integer> currentLevels(List<EmployeeSkill> skills, LocalDate today) {
        if (skills == null) return Map.of();
        Map<UUID, Integer> out = new HashMap<>();
        for (EmployeeSkill s : skills) {
            if (s.validUntil() != null && today != null && s.validUntil().isBefore(today)) continue;
            out.merge(s.competencyId(), s.proficiency(), Math::max);
        }
        return out;
    }

    /**
     * Severity of a single (current vs required) gap:
     * <ul>
     *   <li>current ≥ required → NONE</li>
     *   <li>missing entirely (current = 0): mandatory → BLOCKER, optional → MINOR</li>
     *   <li>otherwise let {@code d = required - current}:
     *       mandatory + d ≥ 2 → BLOCKER, mandatory + d = 1 → MAJOR,
     *       optional  + d ≥ 2 → MAJOR,   optional  + d = 1 → MINOR</li>
     * </ul>
     */
    public static Severity severity(int currentLevel, int requiredLevel, boolean mandatory) {
        if (currentLevel >= requiredLevel) return Severity.NONE;
        if (currentLevel <= 0) return mandatory ? Severity.BLOCKER : Severity.MINOR;
        int d = requiredLevel - currentLevel;
        if (mandatory) return d >= 2 ? Severity.BLOCKER : Severity.MAJOR;
        return d >= 2 ? Severity.MAJOR : Severity.MINOR;
    }

    /**
     * Evaluate each requirement against the employee's current levels. Order
     * follows the input requirement order (the fit score is order-independent).
     */
    public static List<Gap> analyze(List<EmployeeSkill> empSkills,
                                    List<Requirement> requirements,
                                    LocalDate today) {
        if (requirements == null || requirements.isEmpty()) return List.of();
        Map<UUID, Integer> current = currentLevels(empSkills, today);
        List<Gap> out = new ArrayList<>(requirements.size());
        for (Requirement r : requirements) {
            int cur = current.getOrDefault(r.competencyId(), 0);
            out.add(new Gap(r.competencyId(), r.requiredLevel(), cur,
                    r.mandatory(), severity(cur, r.requiredLevel(), r.mandatory())));
        }
        return out;
    }

    /**
     * Numeric fit score for an employee against a position's requirements,
     * 0..100. Used to rank candidates for a vacancy.
     *
     * <p>A BLOCKER on a mandatory competency caps the score at 0 (the candidate
     * cannot be considered). Otherwise each requirement contributes
     * {@code min(current/required, 1) * weight}, where weight is 2 for a
     * mandatory requirement and 1 for an optional one; the total is normalised
     * against the total possible weight. No requirements → 100.
     */
    public static int fitScore(List<EmployeeSkill> empSkills,
                               List<Requirement> requirements,
                               LocalDate today) {
        if (requirements == null || requirements.isEmpty()) return 100;
        Map<UUID, Integer> current = currentLevels(empSkills, today);
        double earned = 0;
        double total = 0;
        for (Requirement r : requirements) {
            int cur = current.getOrDefault(r.competencyId(), 0);
            if (r.mandatory() && severity(cur, r.requiredLevel(), r.mandatory()) == Severity.BLOCKER) {
                return 0;
            }
            double weight = r.mandatory() ? 2.0 : 1.0;
            total += weight;
            double pct = r.requiredLevel() <= 0
                    ? 1.0
                    : Math.min(1.0, (double) cur / (double) r.requiredLevel());
            earned += pct * weight;
        }
        if (total <= 0) return 100;
        return (int) Math.round(earned / total * 100.0);
    }
}
