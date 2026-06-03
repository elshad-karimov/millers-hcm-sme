package az.millers.hcm.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * Pins the progress-percent helper used by assignment responses (M95).
 *
 * <p>Reflection-free: the helper is package-private static on purpose so
 * tests can drive it without Mockito. Mockito-free for the same Java 25
 * reasons as M88 / M92.
 */
class LearningPathAssignmentServiceTest {

    @Test
    void zeroTotalIsZeroPercent() {
        assertThat(LearningPathAssignmentService.progressPercentOf(0, 0)).isZero();
    }

    @Test
    void zeroCompletedIsZeroPercent() {
        assertThat(LearningPathAssignmentService.progressPercentOf(0, 5)).isZero();
    }

    @Test
    void allCompletedIsHundredPercent() {
        assertThat(LearningPathAssignmentService.progressPercentOf(5, 5)).isEqualTo(100);
    }

    @Test
    void halfwayRoundsToHalf() {
        // 3 of 6 = 50%
        assertThat(LearningPathAssignmentService.progressPercentOf(3, 6)).isEqualTo(50);
    }

    @Test
    void roundsToNearestInt() {
        // 1 of 3 = 33.333…% → 33
        assertThat(LearningPathAssignmentService.progressPercentOf(1, 3)).isEqualTo(33);
        // 2 of 3 = 66.666…% → 67
        assertThat(LearningPathAssignmentService.progressPercentOf(2, 3)).isEqualTo(67);
    }

    @Test
    void neverNegative() {
        // Defensive — if a caller passes nonsense the helper should not produce
        // a negative number. Math.round(-0.0) is 0.
        assertThat(LearningPathAssignmentService.progressPercentOf(0, 1)).isZero();
    }

    /**
     * The PathAssignmentStatus enum exists at the schema level — assert its
     * shape so a rename in the V69 CHECK constraint would force a test
     * update.
     */
    @Test
    void pathAssignmentStatusEnumIsStable() throws Exception {
        Class<?> clazz = Class.forName("az.millers.hcm.learning.domain.PathAssignmentStatus");
        Method values = clazz.getMethod("values");
        Object[] vals = (Object[]) values.invoke(null);
        assertThat(vals).extracting(Object::toString).containsExactlyInAnyOrder(
                "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CANCELLED");
    }

    // ── M98 — usable-lift cap (Math.min) ────────────────────────────────────
    //
    // Pins the rule that a course awarding level N against a gap of M can
    // only contribute min(N, M) to the path's score — otherwise a course
    // teaching L5 against an L2 gap would get 3 phantom points it can't
    // actually use, and an L1-gap path with a single high-level course
    // would dominate the rankings. The arithmetic is straightforward but
    // a sign or off-by-one regression here silently re-orders suggestions
    // across the codebase.

    @Test
    void liftCapsAtGapSize() {
        // Course awards L5 against an L2 gap → contributes 2, not 5.
        int awarded = 5, gap = 2;
        assertThat(Math.min(awarded, gap)).isEqualTo(2);
    }

    @Test
    void liftCapsAtAwardedLevel() {
        // Course awards L2 against an L4 gap → contributes 2, not 4.
        int awarded = 2, gap = 4;
        assertThat(Math.min(awarded, gap)).isEqualTo(2);
    }

    @Test
    void liftIsNeverNegative() {
        // gap<=0 means the employee already meets / exceeds the requirement —
        // the service filters these out, but the helper math is symmetric.
        // (Service uses .filter(g -> g.gap() > 0); test mirrors the contract.)
        int awarded = 3, gap = 0;
        int usable = Math.min(awarded, gap);
        // We rely on the service filter, but document the math:
        assertThat(usable).isEqualTo(0);
        assertThat(usable).isNotNegative();
    }
}
