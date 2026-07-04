package az.millers.hcm.timesheet.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetStatus;

public record TimesheetResponse(
        UUID id,
        UUID employeeId,
        int periodYear,
        int periodMonth,
        TimesheetStatus status,
        UUID workflowInstanceId,
        BigDecimal totalWorkedHours,
        BigDecimal totalOvertimeHours,
        BigDecimal totalLeaveDays,
        BigDecimal totalSickDays,
        BigDecimal totalBtDays,
        BigDecimal totalPermissionHrs,
        BigDecimal totalAbsentDays,
        OffsetDateTime generatedAt,
        String generatedBy,
        OffsetDateTime submittedAt,
        String submittedBy,
        OffsetDateTime approvedAt,
        String approvedBy,
        OffsetDateTime lockedAt,
        List<TimesheetDayResponse> days) {

    public static TimesheetResponse from(Timesheet t, List<TimesheetDayResponse> days) {
        return new TimesheetResponse(
                t.getId(), t.getEmployeeId(),
                t.getPeriodYear(), t.getPeriodMonth(),
                t.getStatus(), t.getWorkflowInstanceId(),
                t.getTotalWorkedHours(), t.getTotalOvertimeHours(),
                t.getTotalLeaveDays(), t.getTotalSickDays(), t.getTotalBtDays(),
                t.getTotalPermissionHrs(), t.getTotalAbsentDays(),
                t.getGeneratedAt(), t.getGeneratedBy(),
                t.getSubmittedAt(), t.getSubmittedBy(),
                t.getApprovedAt(), t.getApprovedBy(),
                t.getLockedAt(),
                days);
    }
}
