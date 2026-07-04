package az.millers.hcm.common.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the {@link EffectiveDatedRecord#closeOn(LocalDate)}
 * default method — the single source of truth for closing a prior history
 * slice. Verifies the off-by-one math and the guards that previously had to
 * be redocumented every time a service wrote its own version (M62).
 */
class EffectiveDatedRecordTest {

    /** Minimal in-memory implementation so we can drive the default method. */
    static class Slice implements EffectiveDatedRecord {
        LocalDate from;
        LocalDate to;
        @Override public LocalDate getEffectiveFrom() { return from; }
        @Override public LocalDate getEffectiveTo() { return to; }
        @Override public void setEffectiveTo(LocalDate t) { this.to = t; }
    }

    @Test
    void closeOnSetsEffectiveToToDayBeforeNewStart() {
        Slice s = new Slice();
        s.from = LocalDate.of(2024, 1, 1);
        s.closeOn(LocalDate.of(2024, 6, 15));
        assertThat(s.to).isEqualTo(LocalDate.of(2024, 6, 14));
    }

    @Test
    void closeOnRejectsNullNewStart() {
        Slice s = new Slice();
        s.from = LocalDate.of(2024, 1, 1);
        assertThatThrownBy(() -> s.closeOn(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void closeOnRejectsNewStartOnSameDay() {
        // A same-day re-transition is a legitimate situation but it cannot
        // be modelled by closing — the caller (EmployeeHistoryService) deletes
        // the prior open row instead. We want this guard to fire so we don't
        // silently produce a (from=Jan1, to=Dec31) row when the new start is
        // also Jan1 — which would underflow effectiveTo.
        Slice s = new Slice();
        s.from = LocalDate.of(2024, 1, 1);
        assertThatThrownBy(() -> s.closeOn(LocalDate.of(2024, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly after");
    }

    @Test
    void closeOnRejectsNewStartBeforeOwnStart() {
        Slice s = new Slice();
        s.from = LocalDate.of(2024, 6, 1);
        assertThatThrownBy(() -> s.closeOn(LocalDate.of(2024, 5, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly after");
    }

    @Test
    void isCurrentReflectsEffectiveTo() {
        Slice s = new Slice();
        s.from = LocalDate.of(2024, 1, 1);
        assertThat(s.isCurrent()).isTrue();
        s.to = LocalDate.of(2024, 6, 30);
        assertThat(s.isCurrent()).isFalse();
    }
}
