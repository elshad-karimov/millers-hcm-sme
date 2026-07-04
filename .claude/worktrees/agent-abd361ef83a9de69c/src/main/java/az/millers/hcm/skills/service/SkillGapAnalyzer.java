package az.millers.hcm.skills.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.skills.domain.GapSeverity;

/**
 * M127 — pure-static math for skill-gap analysis. Mockito-free so the
 * priority logic gets pinned by plain JUnit (Java 25 class-file v69
 * isn't supported by Byte Buddy).
 *
 * <p>Two input shapes the service composes:
 * <ul>
 *   <li>{@link EmployeeSkill} — one row per
 *       (employee, competency, source) from
 *       {@code learning.employee_competency},</li>
 *   <li>{@link PositionRequirement} — one row per
 *       (position, competency) from
 *       {@code staffing.position_required_competency}.</li>
 * </ul>
 *
 * <p>The analyzer reduces multiple employee skill rows to "current
 * best level per competency" (max across non-expired sources), then
 * compares to the position requirements and produces a sorted gap
 * list with severity tags.
 */
public final class SkillGapAnalyzer {

    public record EmployeeSkill(
            UUID competencyId,
            int proficiency,
            LocalDate validUntil) {}

    public record PositionRequirement(
            UUID competencyId,
            int requiredLevel,
            boolean mandatory) {}

    public record SkillGap(
            UUID competencyId,
            int requiredLevel,
            /** 0 if the employee has no record for this competency. */
            int currentLevel,
            boolean mandatory,
            GapSeverity severity) {}

    private SkillGapAnalyzer() {}

    /**
     * Collapse multiple
     * {@code learning.employee_competency} rows into "current level per
     * competency" by taking the MAX across non-expired sources.
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
     * Compute the severity of a single (current vs required) gap.
     * Defined as:
     * <ul>
     *   <li>current ≥ required → NONE</li>
     *   <li>missing entirely (current = 0):
     *       <ul><li>mandatory → BLOCKER</li>
     *           <li>optional  → MINOR</li></ul></li>
     *   <li>otherwise let {@code d = required - current}:
     *       <ul><li>mandatory + d ≥ 2 → BLOCKER</li>
     *           <li>mandatory + d = 1 → MAJOR</li>
     *           <li>optional  + d ≥ 2 → MAJOR</li>
     *           <li>optional  + d = 1 → MINOR</li></ul></li>
     * </ul>
     */
    public static GapSeverity severity(int currentLevel, int requiredLevel, boolean mandatory) {
        if (currentLevel >= requiredLevel) return GapSeverity.NONE;
        if (currentLevel <= 0) return mandatory ? GapSeverity.BLOCKER : GapSeverity.MINOR;
        int d = requiredLevel - currentLevel;
        if (mandatory) return d >= 2 ? GapSeverity.BLOCKER : GapSeverity.MAJOR;
        return d >= 2 ? GapSeverity.MAJOR : GapSeverity.MINOR;
    }

    /**
     * Full gap profile: for each requirement, produce one
     * {@link SkillGap}. Sorted with BLOCKER first, then MAJOR, then
     * MINOR, then NONE; within a severity, larger deltas first.
     */
    public static List<SkillGap> analyze(List<EmployeeSkill> empSkills,
                                          List<PositionRequirement> requirements,
                                          LocalDate today) {
        if (requirements == null || requirements.isEmpty()) return List.of();
        Map<UUID, Integer> current = currentLevels(empSkills, today);
        List<SkillGap> out = new ArrayList<>(requirements.size());
        for (PositionRequirement r : requirements) {
            int cur = current.getOrDefault(r.competencyId(), 0);
            GapSeverity sev = severity(cur, r.requiredLevel(), r.mandatory());
            out.add(new SkillGap(r.competencyId(), r.requiredLevel(),
                    cur, r.mandatory(), sev));
        }
        // BLOCKER first → ascending severityOrder. Within a severity,
        // larger deltas first → reversed delta comparator. Tie-break on
        // competency id for a deterministic order across runs.
        Comparator<SkillGap> bySeverity = Comparator.comparingInt(SkillGapAnalyzer::severityOrder);
        Comparator<SkillGap> byDelta = Comparator
                .comparingInt((SkillGap g) -> g.requiredLevel() - g.currentLevel())
                .reversed();
        out.sort(bySeverity.thenComparing(byDelta)
                .thenComparing((SkillGap g) -> g.competencyId().toString()));
        return out;
    }

    /**
     * Numeric fit score for an employee against a position's requirements,
     * 0..100. Used to rank candidates for a vacancy.
     *
     * <p>BLOCKER on a mandatory competency caps the score at 0 (the
     * candidate cannot be considered). Otherwise each requirement
     * contributes {@code min(current/required, 1) * weight}, where
     * weight is 2 for mandatory and 1 for optional. The result is
     * normalised against the total possible weight.
     */
    public static int fitScore(List<EmployeeSkill> empSkills,
                                List<PositionRequirement> requirements,
                                LocalDate today) {
        if (requirements == null || requirements.isEmpty()) return 100;
        Map<UUID, Integer> current = currentLevels(empSkills, today);
        double earned = 0;
        double total = 0;
        for (PositionRequirement r : requirements) {
            int cur = current.getOrDefault(r.competencyId(), 0);
            GapSeverity sev = severity(cur, r.requiredLevel(), r.mandatory());
            if (sev == GapSeverity.BLOCKER && r.mandatory()) return 0;
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

    private static int severityOrder(SkillGap g) {
        return switch (g.severity()) {
            case BLOCKER -> 0;
            case MAJOR -> 1;
            case MINOR -> 2;
            case NONE -> 3;
        };
    }
}
