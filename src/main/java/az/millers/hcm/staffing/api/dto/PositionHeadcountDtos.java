package az.millers.hcm.staffing.api.dto;

import java.util.List;
import java.util.UUID;

import az.millers.hcm.staffing.domain.VacancyState;

/** DTOs for the M109 position-control dashboard. */
public final class PositionHeadcountDtos {

    private PositionHeadcountDtos() {}

    /**
     * Per-position row. {@code approvedHeadcount} / {@code occupiedHeadcount}
     * come straight from the entity; {@code actualOccupied} is the ground-truth
     * count of non-terminated employees. The two should match — when they
     * don't, {@code driftDetected} is true and the reconciliation walker will
     * fix it on its next pass.
     */
    public record PositionHeadcountRow(
            UUID positionId,
            String code,
            String title,
            UUID orgUnitId,
            String orgUnitLabel,
            int approvedHeadcount,
            int occupiedHeadcount,
            int actualOccupied,
            int openVacancyOpenings,
            int remainingCapacity,
            boolean overBudget,
            boolean driftDetected,
            VacancyState vacancyState) {
    }

    /** Per-org-unit roll-up — used by the dashboard "by department" panel. */
    public record OrgUnitRoll(
            UUID orgUnitId,
            String orgUnitLabel,
            int positionCount,
            int approvedHeadcount,
            int actualOccupied,
            int openVacancyOpenings,
            int remainingCapacity) {
    }

    /** Top-level summary returned by {@code GET /staffing/headcount}. */
    public record HeadcountSummary(
            int totalApproved,
            int totalActualOccupied,
            int totalOpenVacancies,
            int totalRemainingCapacity,
            int positionsOverBudget,
            int positionsWithDrift,
            int positionCount,
            List<PositionHeadcountRow> rows,
            List<OrgUnitRoll> byOrgUnit) {
    }
}
