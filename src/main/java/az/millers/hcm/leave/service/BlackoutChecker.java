package az.millers.hcm.leave.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import az.millers.hcm.leave.domain.BlackoutScope;
import az.millers.hcm.leave.domain.BlackoutSeverity;
import az.millers.hcm.leave.domain.BlackoutWindow;

/**
 * M123 — pure-static applicability + severity math for leave blackout
 * windows. Kept Spring-free so the contract gets pinned by plain JUnit
 * (Java 25 class-file v69 isn't supported by Byte Buddy, so Mockito is
 * unavailable).
 *
 * <p>{@link #findApplicable} is the single source of truth used by both
 * {@link LeaveRequestService} on submit and by the preview endpoint
 * that powers the leave-form conflict banner.
 */
public final class BlackoutChecker {

    private BlackoutChecker() {}

    /**
     * Subset of {@code candidates} whose scope + dates apply to a leave
     * request from {@code employeeOrgUnits} for {@code leaveTypeId} over
     * {@code [from, to]}.
     *
     * @param candidates       windows the repository returned for the
     *                         date overlap — already filtered to "active
     *                         and overlapping" by the SQL layer.
     * @param employeeOrgUnits the set of org-unit ids the employee belongs
     *                         to AND every ancestor of those. Empty / null
     *                         means "employee not assigned to a unit" —
     *                         ORG_UNIT scoped windows then don't apply.
     * @param leaveTypeId      the leave type the request is for.
     */
    public static List<BlackoutWindow> findApplicable(Collection<BlackoutWindow> candidates,
                                                      Set<UUID> employeeOrgUnits,
                                                      UUID leaveTypeId,
                                                      LocalDate from,
                                                      LocalDate to) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<BlackoutWindow> out = new ArrayList<>();
        for (BlackoutWindow w : candidates) {
            if (!w.isActive()) continue;
            if (!overlaps(w.getStartDate(), w.getEndDate(), from, to)) continue;
            switch (w.getScope()) {
                case GLOBAL -> out.add(w);
                case LEAVE_TYPE -> {
                    if (leaveTypeId != null && leaveTypeId.equals(w.getLeaveTypeId())) out.add(w);
                }
                case ORG_UNIT -> {
                    if (w.getOrgUnitId() != null
                            && employeeOrgUnits != null
                            && employeeOrgUnits.contains(w.getOrgUnitId())) out.add(w);
                }
            }
        }
        return out;
    }

    /**
     * Inclusive overlap math on two date ranges. Both bounds inclusive
     * — a window {@code [2026-12-20, 2026-12-31]} overlaps a request
     * {@code [2026-12-31, 2027-01-05]}.
     */
    public static boolean overlaps(LocalDate aStart, LocalDate aEnd,
                                   LocalDate bStart, LocalDate bEnd) {
        return !aEnd.isBefore(bStart) && !aStart.isAfter(bEnd);
    }

    /**
     * Worst severity across applicable windows. {@code BLOCK} wins over
     * {@code REQUIRES_APPROVAL}; an empty input returns {@code null} so
     * the caller can early-exit.
     */
    public static BlackoutSeverity worstSeverity(Collection<BlackoutWindow> applicable) {
        if (applicable == null || applicable.isEmpty()) return null;
        BlackoutSeverity worst = null;
        for (BlackoutWindow w : applicable) {
            if (w.getSeverity() == BlackoutSeverity.BLOCK) return BlackoutSeverity.BLOCK;
            worst = BlackoutSeverity.REQUIRES_APPROVAL;
        }
        return worst;
    }

    /**
     * Pretty error message for a BLOCK rejection. Names every blocking
     * window's range so HR can address the right one. Caller passes
     * already-applicable windows.
     */
    public static String formatBlockMessage(Collection<BlackoutWindow> applicable) {
        StringBuilder b = new StringBuilder(
                "Leave is blocked during the following window(s):");
        for (BlackoutWindow w : applicable) {
            if (w.getSeverity() != BlackoutSeverity.BLOCK) continue;
            b.append("\n• ").append(w.getName())
             .append(" (").append(w.getStartDate())
             .append(" to ").append(w.getEndDate());
            if (w.getScope() == BlackoutScope.LEAVE_TYPE) b.append(", leave type");
            if (w.getScope() == BlackoutScope.ORG_UNIT)   b.append(", org unit");
            b.append(")");
            if (w.getReason() != null && !w.getReason().isBlank()) {
                b.append(" — ").append(w.getReason());
            }
        }
        return b.toString();
    }
}
