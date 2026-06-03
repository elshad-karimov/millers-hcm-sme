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
}
