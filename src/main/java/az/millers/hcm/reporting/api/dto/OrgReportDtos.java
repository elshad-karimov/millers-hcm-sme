package az.millers.hcm.reporting.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Org-Structure report DTOs (M81). Distinct file from EmpMgmtDtos so the
 * existing employee-management report family stays narrow.
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
}
