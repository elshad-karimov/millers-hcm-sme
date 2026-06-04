package az.millers.hcm.lifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;

/**
 * Pins the progress-percent calculation used by checklist assignments
 * (M105/M106). The auto-complete rule depends on required-task DONE+SKIPPED
 * count, while the displayed progress percent uses all tasks. Tests cover
 * both axes.
 */
class ChecklistServiceTest {

    private static ChecklistTaskStatus task(ChecklistTaskStatusValue status, boolean required) {
        ChecklistTaskStatus t = new ChecklistTaskStatus();
        t.setStatus(status);
        t.setRequired(required);
        return t;
    }

    @Test
    void emptyListIsZero() {
        assertThat(ChecklistService.progressPercent(List.of())).isZero();
    }

    @Test
    void allPendingIsZero() {
        assertThat(ChecklistService.progressPercent(List.of(
                task(ChecklistTaskStatusValue.PENDING, true),
                task(ChecklistTaskStatusValue.PENDING, true))))
                .isZero();
    }

    @Test
    void allDoneIsHundred() {
        assertThat(ChecklistService.progressPercent(List.of(
                task(ChecklistTaskStatusValue.DONE, true),
                task(ChecklistTaskStatusValue.DONE, true))))
                .isEqualTo(100);
    }

    @Test
    void halfDoneIsFifty() {
        assertThat(ChecklistService.progressPercent(List.of(
                task(ChecklistTaskStatusValue.DONE, true),
                task(ChecklistTaskStatusValue.PENDING, true))))
                .isEqualTo(50);
    }

    @Test
    void skippedCountsAsCompleted() {
        // Optional / skipped tasks should be counted as completed for the
        // progress percent — they're no longer blocking.
        assertThat(ChecklistService.progressPercent(List.of(
                task(ChecklistTaskStatusValue.SKIPPED, true),
                task(ChecklistTaskStatusValue.DONE, true))))
                .isEqualTo(100);
    }

    @Test
    void inProgressIsNotCompleted() {
        // IN_PROGRESS is started but not done — does NOT count toward progress.
        assertThat(ChecklistService.progressPercent(List.of(
                task(ChecklistTaskStatusValue.IN_PROGRESS, true),
                task(ChecklistTaskStatusValue.PENDING, true))))
                .isZero();
    }

    @Test
    void roundsHalfUp() {
        // 1 of 3 = 33.333…% → 33; 2 of 3 = 66.666…% → 67.
        assertThat(ChecklistService.progressPercent(List.of(
                task(ChecklistTaskStatusValue.DONE, true),
                task(ChecklistTaskStatusValue.PENDING, true),
                task(ChecklistTaskStatusValue.PENDING, true))))
                .isEqualTo(33);
        assertThat(ChecklistService.progressPercent(List.of(
                task(ChecklistTaskStatusValue.DONE, true),
                task(ChecklistTaskStatusValue.DONE, true),
                task(ChecklistTaskStatusValue.PENDING, true))))
                .isEqualTo(67);
    }
}
