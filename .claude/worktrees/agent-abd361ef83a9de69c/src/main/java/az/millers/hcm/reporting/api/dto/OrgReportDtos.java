package az.millers.hcm.reporting.api.dto;

import java.util.List;
import java.util.UUID;

import java.time.LocalDate;

import az.millers.hcm.organization.domain.OrgUnitLifecycleState;

/**
 * Org-Structure report DTOs (M81 + M145).
 * Distinct file from EmpMgmtDtos so the existing employee-management
 * report family stays narrow.
 */
public final class OrgReportDtos {
    private OrgReportDtos() {}

    /** One row per manager with at least one direct report. */
    public record SpanOfControlRow(
            UUID managerId,
            String employeeNo,
            String fullName,
            String positionTitle,
            String departmentName,
            int directReports,
            int transitiveReports,
            int depth,
            /** OK / OVERSPAN / UNDERSPAN — computed against thresholds. */
            String flag) {}

    public record SpanOfControlReport(
            int managersCount,
            long overspanCount,
            long underspanCount,
            int overspanThreshold,
            int underspanThreshold,
            List<SpanOfControlRow> rows) {}

    // ── M145 — Org-native reports (§35) ──────────────────────────────────────

    /** One row in the headcount-vs-budget report. */
    public record HeadcountRow(
            UUID unitId,
            String code,
            String name,
            String unitType,
            OrgUnitLifecycleState lifecycleState,
            /** {@code null} when no budget has been set on the unit. */
            Integer headcountBudget,
            int actualHeadcount,
            /** budget − actual; null when headcountBudget is null. */
            Integer variance) {}

    public record HeadcountReport(
            int totalBudget,
            int totalActual,
            Integer totalVariance,
            List<HeadcountRow> rows) {}

    /** One row in the HRBP-coverage report. */
    public record HrbpCoverageRow(
            UUID unitId,
            String code,
            String name,
            String unitType,
            UUID hrbpId,
            /** Resolved display name of the HRBP, or null when none assigned. */
            String hrbpName,
            boolean hasHrbp) {}

    public record HrbpCoverageReport(
            int totalUnits,
            int unitsWithHrbp,
            int unitsWithoutHrbp,
            double coveragePct,
            List<HrbpCoverageRow> rows) {}

    /** Aggregated distribution of units by lifecycle state and type. */
    public record OrgDistributionReport(
            java.util.Map<String, Long> byLifecycleState,
            java.util.Map<String, Long> byUnitType) {}

    /** Flat export row — one per unit, all attributes included. */
    public record OrgUnitFlatRow(
            UUID unitId,
            String code,
            String name,
            String unitType,
            String parentCode,
            String lifecycleState,
            String legalEntityCode,
            String locationCode,
            String hrbpEmployeeNo,
            String costCentreCode,
            String contactEmail,
            Integer headcountBudget,
            int actualHeadcount,
            LocalDate closureAnnouncedDate,
            LocalDate closedDate) {}

    public record OrgFlatReport(List<OrgUnitFlatRow> rows) {}
}
