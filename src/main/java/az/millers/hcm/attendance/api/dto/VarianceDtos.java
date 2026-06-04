package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.attendance.service.VarianceCategory;

/** DTOs for the M113 roster variance dashboard. */
public final class VarianceDtos {

    private VarianceDtos() {}

    /** Per-(employee, date) cell — drives the heatmap. */
    public record VarianceCell(
            UUID employeeId,
            LocalDate workDate,
            VarianceCategory category,
            int lateMinutes,
            int earlyMinutes,
            int overtimeMinutes) {
    }

    /** Per-employee roll-up over the window. */
    public record EmployeeRoll(
            UUID employeeId,
            String employeeName,
            String orgUnitLabel,
            int rosteredDays,
            int onTime,
            int late,
            int earlyLeave,
            int unplannedOt,
            int noShow,
            int totalLateMinutes,
            int totalEarlyMinutes,
            int totalOvertimeMinutes) {

        /** Days with any variance — drives the "top offenders" ordering. */
        public int variantDays() {
            return late + earlyLeave + unplannedOt + noShow;
        }
    }

    /** Top-level numbers + per-employee + per-date rows. */
    public record VarianceReport(
            LocalDate from,
            LocalDate to,
            int rosteredRowsScanned,
            Map<VarianceCategory, Integer> totals,
            List<EmployeeRoll> byEmployee,
            List<VarianceCell> cells) {
    }
}
