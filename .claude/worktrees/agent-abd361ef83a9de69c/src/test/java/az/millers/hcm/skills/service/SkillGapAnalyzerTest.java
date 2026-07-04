package az.millers.hcm.skills.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.skills.domain.GapSeverity;
import az.millers.hcm.skills.service.SkillGapAnalyzer.EmployeeSkill;
import az.millers.hcm.skills.service.SkillGapAnalyzer.PositionRequirement;
import az.millers.hcm.skills.service.SkillGapAnalyzer.SkillGap;

/**
 * M127 — pins the severity ladder, the "current level = max across
 * non-expired sources" rule, the sort order BLOCKER→NONE, and the
 * fit-score caps. These are the only places production code makes
 * judgment calls, so they're worth nailing down.
 */
class SkillGapAnalyzerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 5);

    // ── severity ladder ───────────────────────────────────────────────────

    @Test
    void severityNoneWhenMet() {
        assertThat(SkillGapAnalyzer.severity(4, 4, true)).isEqualTo(GapSeverity.NONE);
        assertThat(SkillGapAnalyzer.severity(5, 4, false)).isEqualTo(GapSeverity.NONE);
    }

    @Test
    void severityBlockerWhenMissingMandatory() {
        assertThat(SkillGapAnalyzer.severity(0, 3, true)).isEqualTo(GapSeverity.BLOCKER);
    }

    @Test
    void severityMinorWhenMissingOptional() {
        assertThat(SkillGapAnalyzer.severity(0, 3, false)).isEqualTo(GapSeverity.MINOR);
    }

    @Test
    void severityMandatoryGapByOne() {
        assertThat(SkillGapAnalyzer.severity(3, 4, true)).isEqualTo(GapSeverity.MAJOR);
    }

    @Test
    void severityMandatoryGapByTwoOrMore() {
        assertThat(SkillGapAnalyzer.severity(2, 4, true)).isEqualTo(GapSeverity.BLOCKER);
        assertThat(SkillGapAnalyzer.severity(1, 5, true)).isEqualTo(GapSeverity.BLOCKER);
    }

    @Test
    void severityOptionalGapByOne() {
        assertThat(SkillGapAnalyzer.severity(3, 4, false)).isEqualTo(GapSeverity.MINOR);
    }

    @Test
    void severityOptionalGapByTwoOrMore() {
        assertThat(SkillGapAnalyzer.severity(2, 4, false)).isEqualTo(GapSeverity.MAJOR);
    }

    // ── currentLevels: max + expiry ───────────────────────────────────────

    @Test
    void currentLevelsTakesMaxAcrossSources() {
        UUID c = UUID.randomUUID();
        var levels = SkillGapAnalyzer.currentLevels(List.of(
                new EmployeeSkill(c, 2, null),
                new EmployeeSkill(c, 4, null),
                new EmployeeSkill(c, 3, null)
        ), TODAY);
        assertThat(levels.get(c)).isEqualTo(4);
    }

    @Test
    void currentLevelsIgnoresExpired() {
        UUID c = UUID.randomUUID();
        var levels = SkillGapAnalyzer.currentLevels(List.of(
                new EmployeeSkill(c, 5, TODAY.minusDays(1)), // expired
                new EmployeeSkill(c, 3, TODAY.plusDays(30))  // still valid
        ), TODAY);
        assertThat(levels.get(c)).isEqualTo(3);
    }

    @Test
    void currentLevelsAllExpiredYieldsAbsent() {
        UUID c = UUID.randomUUID();
        var levels = SkillGapAnalyzer.currentLevels(List.of(
                new EmployeeSkill(c, 5, TODAY.minusYears(1))
        ), TODAY);
        assertThat(levels).doesNotContainKey(c);
    }

    @Test
    void currentLevelsNullInputReturnsEmpty() {
        assertThat(SkillGapAnalyzer.currentLevels(null, TODAY)).isEmpty();
    }

    // ── analyze: ordering ────────────────────────────────────────────────

    @Test
    void analyzeSortsBlockerFirstThenMajorThenMinorThenNone() {
        UUID a = UUID.randomUUID(); // mandatory, missing → BLOCKER
        UUID b = UUID.randomUUID(); // mandatory, gap-1 → MAJOR
        UUID c = UUID.randomUUID(); // optional, gap-1 → MINOR
        UUID d = UUID.randomUUID(); // met → NONE
        List<SkillGap> gaps = SkillGapAnalyzer.analyze(
                List.of(
                        new EmployeeSkill(b, 2, null),
                        new EmployeeSkill(c, 2, null),
                        new EmployeeSkill(d, 3, null)),
                List.of(
                        new PositionRequirement(a, 3, true),
                        new PositionRequirement(b, 3, true),
                        new PositionRequirement(c, 3, false),
                        new PositionRequirement(d, 3, true)),
                TODAY);
        assertThat(gaps).extracting(SkillGap::competencyId)
                .containsExactly(a, b, c, d);
    }

    @Test
    void analyzeEmptyRequirementsReturnsEmpty() {
        assertThat(SkillGapAnalyzer.analyze(List.of(), List.of(), TODAY)).isEmpty();
    }

    // ── fitScore ─────────────────────────────────────────────────────────

    @Test
    void fitScore100WhenEveryRequirementMet() {
        UUID c = UUID.randomUUID();
        int s = SkillGapAnalyzer.fitScore(
                List.of(new EmployeeSkill(c, 4, null)),
                List.of(new PositionRequirement(c, 4, true)),
                TODAY);
        assertThat(s).isEqualTo(100);
    }

    @Test
    void fitScoreZeroWhenMandatoryBlocker() {
        UUID mandatoryMissing = UUID.randomUUID();
        UUID optionalMet = UUID.randomUUID();
        int s = SkillGapAnalyzer.fitScore(
                List.of(new EmployeeSkill(optionalMet, 4, null)),
                List.of(
                        new PositionRequirement(mandatoryMissing, 3, true),
                        new PositionRequirement(optionalMet, 4, false)),
                TODAY);
        assertThat(s).isZero();
    }

    @Test
    void fitScorePartialCreditOnOptionalGap() {
        // BLOCKER on mandatory caps the score at 0 (see
        // fitScoreZeroWhenMandatoryBlocker). To exercise partial credit
        // we use an OPTIONAL gap and a met mandatory.
        UUID optionalGap = UUID.randomUUID();
        UUID mandatoryMet = UUID.randomUUID();
        // optionalGap: required 4, current 2 → 50% × weight 1 = 0.5 of 1.0
        // mandatoryMet: required 4, current 4 → 100% × weight 2 = 2.0 of 2.0
        // total = 2.5 / 3.0 ≈ 83
        int s = SkillGapAnalyzer.fitScore(
                List.of(
                        new EmployeeSkill(optionalGap, 2, null),
                        new EmployeeSkill(mandatoryMet, 4, null)),
                List.of(
                        new PositionRequirement(optionalGap, 4, false),
                        new PositionRequirement(mandatoryMet, 4, true)),
                TODAY);
        assertThat(s).isBetween(80, 90);
    }

    @Test
    void fitScore100ForEmptyRequirements() {
        assertThat(SkillGapAnalyzer.fitScore(List.of(), List.of(), TODAY)).isEqualTo(100);
    }

    @Test
    void fitScoreMandatoryGapByOneNotBlockerStillCountsPartial() {
        // gap-by-1 on mandatory = MAJOR, not BLOCKER. Score should still
        // get partial credit, not be zeroed.
        UUID c = UUID.randomUUID();
        int s = SkillGapAnalyzer.fitScore(
                List.of(new EmployeeSkill(c, 3, null)),
                List.of(new PositionRequirement(c, 4, true)),
                TODAY);
        assertThat(s).isBetween(70, 80);
    }
}
