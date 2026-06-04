package az.millers.hcm.compbenefits.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.CycleRequest;
import az.millers.hcm.compbenefits.domain.CompCycleStatus;

/**
 * Pins the M118 cycle validation + state machine.
 *
 * <p>The state machine matters because reopening a CLOSED or CANCELLED
 * cycle would let new proposals change results that have already been
 * approved and written to EmployeeCompensation.
 */
class CompPlanningServiceTest {

    // ── validateCycle() ─────────────────────────────────────────────────

    @Test
    void validateCycleAcceptsCanonical() {
        CycleRequest req = req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new BigDecimal("100000"));
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycle(req));
    }

    @Test
    void validateCycleRejectsNullDates() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycle(
                        req(null, LocalDate.of(2026, 12, 31), BigDecimal.ZERO)));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycle(
                        req(LocalDate.of(2026, 1, 1), null, BigDecimal.ZERO)));
    }

    @Test
    void validateCycleRejectsInvertedRange() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycle(
                        req(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), BigDecimal.ZERO)))
                .withMessageContaining("closesOn must be on or after opensOn");
    }

    @Test
    void validateCycleAcceptsSameDay() {
        // A single-day cycle is unusual but legitimate (correction cycle).
        LocalDate d = LocalDate.of(2026, 6, 1);
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycle(
                req(d, d, BigDecimal.ZERO)));
    }

    @Test
    void validateCycleRejectsNegativePool() {
        CycleRequest req = req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("-1"));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycle(req))
                .withMessageContaining("poolTotal must be non-negative");
    }

    @Test
    void validateCycleAllowsZeroPool() {
        // A pool of zero is a planning exercise — no actual budget yet.
        CycleRequest req = req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), BigDecimal.ZERO);
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycle(req));
    }

    @Test
    void validateCycleAllowsNullPool() {
        // Optional in the API; treated as zero by the apply step.
        CycleRequest req = req(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycle(req));
    }

    // ── validateCycleTransition() ───────────────────────────────────────

    @Test
    void transitionDraftToOpenAllowed() {
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycleTransition(
                CompCycleStatus.DRAFT, CompCycleStatus.OPEN));
    }

    @Test
    void transitionDraftToCancelledAllowed() {
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycleTransition(
                CompCycleStatus.DRAFT, CompCycleStatus.CANCELLED));
    }

    @Test
    void transitionOpenToClosedAllowed() {
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycleTransition(
                CompCycleStatus.OPEN, CompCycleStatus.CLOSED));
    }

    @Test
    void transitionOpenToCancelledAllowed() {
        assertThatNoException().isThrownBy(() -> CompPlanningService.validateCycleTransition(
                CompCycleStatus.OPEN, CompCycleStatus.CANCELLED));
    }

    @Test
    void transitionDraftToClosedRejected() {
        // Can't skip OPEN — every closed cycle must have had proposals.
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycleTransition(
                        CompCycleStatus.DRAFT, CompCycleStatus.CLOSED));
    }

    @Test
    void transitionClosedReopenRejected() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycleTransition(
                        CompCycleStatus.CLOSED, CompCycleStatus.OPEN));
    }

    @Test
    void transitionCancelledReopenRejected() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycleTransition(
                        CompCycleStatus.CANCELLED, CompCycleStatus.OPEN));
    }

    @Test
    void transitionOpenToDraftRejected() {
        // Going backwards corrupts whatever proposals are mid-flight.
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> CompPlanningService.validateCycleTransition(
                        CompCycleStatus.OPEN, CompCycleStatus.DRAFT));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static CycleRequest req(LocalDate opens, LocalDate closes, BigDecimal pool) {
        return new CycleRequest("Q1-2026", "Test", null, opens, closes, pool, "AZN");
    }
}
