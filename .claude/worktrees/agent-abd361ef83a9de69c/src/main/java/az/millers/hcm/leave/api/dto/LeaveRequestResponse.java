package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveRequestStatus;

public record LeaveRequestResponse(
        UUID id,
        String requestNo,
        UUID employeeId,
        UUID leaveTypeId,
        LocalDate startDate,
        LocalDate endDate,
        boolean halfDay,
        BigDecimal totalDays,
        String reason,
        String attachmentUrl,
        UUID replacementEmployeeId,
        LeaveRequestStatus status,
        UUID workflowInstanceId,
        /** M341: For HOURS-unit types — start time. */
        LocalTime startTime,
        /** M341: For HOURS-unit types — end time. */
        LocalTime endTime,
        /** M341: For HOURS-unit types — gross hours requested. */
        BigDecimal durationHours,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy) {

    public static LeaveRequestResponse from(LeaveRequest r) {
        return new LeaveRequestResponse(
                r.getId(), r.getRequestNo(), r.getEmployeeId(), r.getLeaveTypeId(),
                r.getStartDate(), r.getEndDate(), r.isHalfDay(), r.getTotalDays(),
                r.getReason(), r.getAttachmentUrl(), r.getReplacementEmployeeId(),
                r.getStatus(), r.getWorkflowInstanceId(),
                r.getStartTime(), r.getEndTime(), r.getDurationHours(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getCreatedBy());
    }
}
