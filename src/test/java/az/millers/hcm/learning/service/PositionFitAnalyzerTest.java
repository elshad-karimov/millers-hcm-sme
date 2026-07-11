package az.millers.hcm.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.learning.service.PositionFitAnalyzer.EmployeeSkill;
import az.millers.hcm.learning.service.PositionFitAnalyzer.Requirement;
import az.millers.hcm.learning.service.PositionFitAnalyzer.Severity;

/**
 * Pins the candidate-fit scoring math (rebuilt from the retired
 * {@code skills.SkillGapAnalyzer}). Pure JUnit + AssertJ — Mockito-free for the
 * same Java 25 reasons as the other learning analyzer tests.
 */
class PositionFitAnalyzerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 12);

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static EmployeeSkill skill(UUID id, int level) {
        return new EmployeeSkill(id, level, null);
    }

    private static Requirement req(UUID id, int level, boolean mandatory) {
        return new Requirement(id, level, mandatory);
    }

    // ── fitScore ─────────────────────────────────────────────────────────────

    @Test
    void noRequirementsIsFullFit() {
        assertThat(PositionFitAnalyzer.fitScore(List.of(), List.of(), TODAY)).isEqualTo(100);
    }

    @Test
    void allRequirementsMetIsHundred() {
        var skills = List.of(skill(A, 4), skill(B, 3));
        var reqs = List.of(req(A, 4, true), req(B, 3, false));
        assertThat(PositionFitAnalyzer.fitScore(skills, reqs, TODAY)).isEqualTo(100);
    }

    @Test
    void exceedingRequirementDoesNotOverflowPastHundred() {
        var skills = List.of(skill(A, 5));
        var reqs = List.of(req(A, 3, true));
        // pct capped at 1.0 → still exactly 100, not 166.
        assertThat(PositionFitAnalyzer.fitScore(skills, reqs, TODAY)).isEqualTo(100);
    }

    @Test
    void missingMandatoryCompetencyCapsScoreAtZero() {
        var skills = List.of(skill(A, 4)); // has A, entirely missing mandatory B
        var reqs = List.of(req(A, 4, true), req(B, 3, true));
        assertThat(PositionFitAnalyzer.fitScore(skills, reqs, TODAY)).isZero();
    }

    @Test
    void mandatoryShortByTwoBlocksAndZeroesScore() {
        var skills = List.of(skill(A, 1)); // required 3, short by 2 → BLOCKER
        var reqs = List.of(req(A, 3, true));
        assertThat(PositionFitAnalyzer.fitScore(skills, reqs, TODAY)).isZero();
    }

    @Test
    void missingOptionalCompetencyDoesNotZeroTheScore() {
        // A mandatory & met; B optional & missing → weighted: earned 2 / total 3.
        var skills = List.of(skill(A, 4));
        var reqs = List.of(req(A, 4, true), req(B, 3, false));
        // (1.0*2 + 0.0*1) / 3 = 0.6667 → 67
        assertThat(PositionFitAnalyzer.fitScore(skills, reqs, TODAY)).isEqualTo(67);
    }

    @Test
    void mandatoryWeightsDoubleAgainstOptional() {
        // Mandatory A short by 1 (2/3 → MAJOR, not blocking), optional B fully met.
        // earned = (2/3)*2 + 1.0*1 = 2.3333 ; total = 3 → 0.7778 → 78. The
        // mandatory requirement's weight of 2 pulls the score more than the
        // optional one would.
        var skills = List.of(skill(A, 2), skill(B, 3));
        var reqs = List.of(req(A, 3, true), req(B, 3, false));
        assertThat(PositionFitAnalyzer.fitScore(skills, reqs, TODAY)).isEqualTo(78);
    }

    @Test
    void expiredCompetencyIsIgnored() {
        var expired = new EmployeeSkill(A, 5, TODAY.minusDays(1));
        var reqs = List.of(req(A, 3, true)); // now missing mandatory → 0
        assertThat(PositionFitAnalyzer.fitScore(List.of(expired), reqs, TODAY)).isZero();
    }

    @Test
    void highestNonExpiredLevelWins() {
        var low = skill(A, 2);
        var high = skill(A, 4);
        var reqs = List.of(req(A, 4, true));
        assertThat(PositionFitAnalyzer.fitScore(List.of(low, high), reqs, TODAY)).isEqualTo(100);
    }

    @Test
    void betterCandidateScoresHigherThanWeakerOne() {
        var reqs = List.of(req(A, 4, true), req(B, 3, false));
        int strong = PositionFitAnalyzer.fitScore(List.of(skill(A, 4), skill(B, 3)), reqs, TODAY);
        int weak = PositionFitAnalyzer.fitScore(List.of(skill(A, 2), skill(B, 1)), reqs, TODAY);
        assertThat(strong).isGreaterThan(weak);
    }

    // ── severity ─────────────────────────────────────────────────────────────

    @Test
    void severityRules() {
        assertThat(PositionFitAnalyzer.severity(4, 3, true)).isEqualTo(Severity.NONE);   // meets
        assertThat(PositionFitAnalyzer.severity(0, 3, true)).isEqualTo(Severity.BLOCKER); // missing mandatory
        assertThat(PositionFitAnalyzer.severity(0, 3, false)).isEqualTo(Severity.MINOR);  // missing optional
        assertThat(PositionFitAnalyzer.severity(1, 3, true)).isEqualTo(Severity.BLOCKER); // mandatory short by 2
        assertThat(PositionFitAnalyzer.severity(2, 3, true)).isEqualTo(Severity.MAJOR);   // mandatory short by 1
        assertThat(PositionFitAnalyzer.severity(1, 3, false)).isEqualTo(Severity.MAJOR);  // optional short by 2
        assertThat(PositionFitAnalyzer.severity(2, 3, false)).isEqualTo(Severity.MINOR);  // optional short by 1
    }

    // ── analyze (severity counts feed the ranking rows) ──────────────────────

    @Test
    void analyzeTagsEachRequirement() {
        var skills = List.of(skill(A, 4), skill(B, 2));
        var reqs = List.of(req(A, 4, true), req(B, 4, true), req(C, 3, false));
        var gaps = PositionFitAnalyzer.analyze(skills, reqs, TODAY);
        assertThat(gaps).hasSize(3);
        long blockers = gaps.stream().filter(g -> g.severity() == Severity.BLOCKER).count();
        long majors = gaps.stream().filter(g -> g.severity() == Severity.MAJOR).count();
        // A met (NONE); B mandatory short by 2 (BLOCKER); C optional missing (MINOR)
        assertThat(blockers).isEqualTo(1);
        assertThat(majors).isZero();
    }
}
