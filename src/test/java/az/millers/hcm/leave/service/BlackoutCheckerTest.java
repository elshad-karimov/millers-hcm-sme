package az.millers.hcm.leave.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.leave.domain.BlackoutScope;
import az.millers.hcm.leave.domain.BlackoutSeverity;
import az.millers.hcm.leave.domain.BlackoutWindow;

/**
 * M123 — pure-static math the submit hook and the preview endpoint
 * both lean on. Pins:
 * <ul>
 *   <li>inclusive overlap on both bounds,</li>
 *   <li>GLOBAL scope applies regardless of employee org/leave-type,</li>
 *   <li>ORG_UNIT scope requires the employee's ancestor chain to
 *       contain the window's unit,</li>
 *   <li>LEAVE_TYPE scope requires the leave type to match,</li>
 *   <li>worstSeverity returns BLOCK over REQUIRES_APPROVAL,</li>
 *   <li>inactive windows are dropped,</li>
 *   <li>formatBlockMessage names every blocking window with its range.</li>
 * </ul>
 */
class BlackoutCheckerTest {

    private static BlackoutWindow win(BlackoutScope scope, BlackoutSeverity sev,
                                       LocalDate start, LocalDate end,
                                       UUID orgUnit, UUID type) {
        BlackoutWindow w = new BlackoutWindow();
        w.setId(UUID.randomUUID());
        w.setName(scope + "/" + sev);
        w.setScope(scope);
        w.setSeverity(sev);
        w.setStartDate(start);
        w.setEndDate(end);
        w.setOrgUnitId(orgUnit);
        w.setLeaveTypeId(type);
        w.setActive(true);
        return w;
    }

    private static final LocalDate D = LocalDate.of(2026, 12, 20);
    private static final LocalDate E = LocalDate.of(2026, 12, 31);

    // ── overlaps ────────────────────────────────────────────────────────────

    @Test
    void overlapInclusiveOnStart() {
        assertThat(BlackoutChecker.overlaps(D, E, E, E.plusDays(5))).isTrue();
    }

    @Test
    void overlapInclusiveOnEnd() {
        assertThat(BlackoutChecker.overlaps(D, E, D.minusDays(5), D)).isTrue();
    }

    @Test
    void noOverlapStrictAfter() {
        assertThat(BlackoutChecker.overlaps(D, E, E.plusDays(1), E.plusDays(5))).isFalse();
    }

    @Test
    void noOverlapStrictBefore() {
        assertThat(BlackoutChecker.overlaps(D, E, D.minusDays(10), D.minusDays(1))).isFalse();
    }

    // ── findApplicable ──────────────────────────────────────────────────────

    @Test
    void globalAlwaysApplies() {
        BlackoutWindow w = win(BlackoutScope.GLOBAL, BlackoutSeverity.BLOCK, D, E, null, null);
        List<BlackoutWindow> hits = BlackoutChecker.findApplicable(
                List.of(w), Set.of(), UUID.randomUUID(), D, D);
        assertThat(hits).containsExactly(w);
    }

    @Test
    void leaveTypeScopeOnlyMatchesSameType() {
        UUID type = UUID.randomUUID();
        BlackoutWindow w = win(BlackoutScope.LEAVE_TYPE, BlackoutSeverity.BLOCK, D, E, null, type);
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(), type, D, D))
                .containsExactly(w);
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(), UUID.randomUUID(), D, D))
                .isEmpty();
    }

    @Test
    void orgUnitScopeRequiresAncestorMatch() {
        UUID unit = UUID.randomUUID();
        UUID otherUnit = UUID.randomUUID();
        BlackoutWindow w = win(BlackoutScope.ORG_UNIT, BlackoutSeverity.BLOCK, D, E, unit, null);
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(unit), null, D, D))
                .containsExactly(w);
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(otherUnit), null, D, D))
                .isEmpty();
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(), null, D, D))
                .isEmpty();
    }

    @Test
    void orgUnitScopePicksUpAncestor() {
        UUID parent = UUID.randomUUID();
        UUID leaf = UUID.randomUUID();
        BlackoutWindow w = win(BlackoutScope.ORG_UNIT, BlackoutSeverity.BLOCK, D, E, parent, null);
        // Employee is at the leaf, but their ancestor chain contains parent.
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(leaf, parent), null, D, D))
                .containsExactly(w);
    }

    @Test
    void inactiveWindowsAreDropped() {
        BlackoutWindow w = win(BlackoutScope.GLOBAL, BlackoutSeverity.BLOCK, D, E, null, null);
        w.setActive(false);
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(), null, D, D)).isEmpty();
    }

    @Test
    void datesOutsideRangeAreDropped() {
        BlackoutWindow w = win(BlackoutScope.GLOBAL, BlackoutSeverity.BLOCK,
                D, E, null, null);
        assertThat(BlackoutChecker.findApplicable(List.of(w), Set.of(), null,
                E.plusDays(1), E.plusDays(5))).isEmpty();
    }

    @Test
    void emptyCandidatesReturnsEmpty() {
        assertThat(BlackoutChecker.findApplicable(List.of(), Set.of(), null, D, E)).isEmpty();
        assertThat(BlackoutChecker.findApplicable(null, Set.of(), null, D, E)).isEmpty();
    }

    // ── worstSeverity ───────────────────────────────────────────────────────

    @Test
    void worstSeverityIsBlockOverApproval() {
        BlackoutWindow a = win(BlackoutScope.GLOBAL, BlackoutSeverity.REQUIRES_APPROVAL, D, E, null, null);
        BlackoutWindow b = win(BlackoutScope.GLOBAL, BlackoutSeverity.BLOCK, D, E, null, null);
        assertThat(BlackoutChecker.worstSeverity(List.of(a, b))).isEqualTo(BlackoutSeverity.BLOCK);
        assertThat(BlackoutChecker.worstSeverity(List.of(b, a))).isEqualTo(BlackoutSeverity.BLOCK);
    }

    @Test
    void worstSeverityNullOnEmpty() {
        assertThat(BlackoutChecker.worstSeverity(List.of())).isNull();
        assertThat(BlackoutChecker.worstSeverity(null)).isNull();
    }

    @Test
    void worstSeverityApprovalOnly() {
        BlackoutWindow w = win(BlackoutScope.GLOBAL, BlackoutSeverity.REQUIRES_APPROVAL, D, E, null, null);
        assertThat(BlackoutChecker.worstSeverity(List.of(w))).isEqualTo(BlackoutSeverity.REQUIRES_APPROVAL);
    }

    // ── formatBlockMessage ─────────────────────────────────────────────────

    @Test
    void blockMessageNamesWindow() {
        BlackoutWindow w = win(BlackoutScope.GLOBAL, BlackoutSeverity.BLOCK, D, E, null, null);
        w.setName("Year-end close");
        w.setReason("Finance month-end window");
        String msg = BlackoutChecker.formatBlockMessage(List.of(w));
        assertThat(msg).contains("Year-end close").contains("2026-12-20").contains("2026-12-31")
                .contains("Finance month-end window");
    }

    @Test
    void blockMessageOnlyIncludesBlockSeverity() {
        BlackoutWindow block = win(BlackoutScope.GLOBAL, BlackoutSeverity.BLOCK, D, E, null, null);
        block.setName("X");
        BlackoutWindow approval = win(BlackoutScope.GLOBAL, BlackoutSeverity.REQUIRES_APPROVAL, D, E, null, null);
        approval.setName("Y");
        String msg = BlackoutChecker.formatBlockMessage(List.of(block, approval));
        assertThat(msg).contains("X").doesNotContain("Y");
    }
}
