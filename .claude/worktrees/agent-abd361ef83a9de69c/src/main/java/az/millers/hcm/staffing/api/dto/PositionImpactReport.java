package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * M272 — PRD §43 Position Impact Analysis.
 *
 * <p>Snapshot of everything that would be affected if an action were
 * taken on a position right now. Drives the SPA "what happens if I
 * close this position?" confirm dialogs for CLOSE / FREEZE / SPLIT /
 * MERGE, and powers the dashboard tile listing positions whose
 * closure would have the biggest blast radius.
 */
public record PositionImpactReport(
        UUID positionId,
        String positionCode,
        String positionTitle,

        // ── Current state snapshot ────────────────────────────────────
        int approvedHeadcount,
        int occupiedHeadcount,
        int vacantHeadcount,
        String status,
        boolean criticalFlag,
        boolean successorRequired,
        Short businessImpactScore,

        // ── What's affected ──────────────────────────────────────────
        /** Active occupants (employees with this position_id, not terminated). */
        List<AffectedEmployee> occupants,
        /** Direct reports whose manager is an occupant of this position. */
        List<AffectedEmployee> directReports,
        /** Open / published vacancies on this position. */
        List<AffectedVacancy> openVacancies,
        /** Pending or active profile grants on this position's occupants. */
        int activeProfileGrants,

        // ── Financial impact ─────────────────────────────────────────
        /** Sum of M244 budget components for this position, if a budget is set. */
        BigDecimal monthlyBudgeted,
        String currency,

        // ── Succession warnings ──────────────────────────────────────
        int activeSuccessorNominations,
        /** True when criticalFlag = true OR successorRequired = true and no READY_NOW nominees. */
        boolean successionAtRisk) {

    public record AffectedEmployee(UUID id, String employeeNo, String fullName) {}

    public record AffectedVacancy(UUID id, String vacancyNo, String title, String status, int openings) {}
}
