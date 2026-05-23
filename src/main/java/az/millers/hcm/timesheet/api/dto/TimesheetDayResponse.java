package az.millers.hcm.timesheet.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.timesheet.domain.TimesheetDay;

public record TimesheetDayResponse(
        UUID id,
        LocalDate workDate,
        String primaryCode,
        BigDecimal workedHours,
        BigDecimal overtimeHours,
        BigDecimal breakHours,
        int lateMinutes,
        int earlyMinutes,
        UUID sourceSummaryId,
        UUID leaveRequestId,
        UUID btRequestId,
        UUID permissionRequestId,
        String anomalies,
        String correctionReason,
        String correctedBy,
        OffsetDateTime correctedAt) {

    public static TimesheetDayResponse from(TimesheetDay d) {
        return new TimesheetDayResponse(
                d.getId(), d.getWorkDate(), d.getPrimaryCode(),
                d.getWorkedHours(), d.getOvertimeHours(), d.getBreakHours(),
                d.getLateMinutes(), d.getEarlyMinutes(),
                d.getSourceSummaryId(), d.getLeaveRequestId(), d.getBtRequestId(),
                d.getPermissionRequestId(),
                d.getAnomalies(),
                d.getCorrectionReason(), d.getCorrectedBy(), d.getCorrectedAt());
    }
}
