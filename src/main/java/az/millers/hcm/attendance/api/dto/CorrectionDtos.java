package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.AttendanceCorrectionRequest;

public class CorrectionDtos {

    public record CorrectionRequest(
            UUID employeeId,
            LocalDate workDate,
            UUID summaryId,
            OffsetDateTime requestedClockIn,
            OffsetDateTime requestedClockOut,
            String requestedStatus,
            String reason,
            String correctionType,
            boolean absenceStatusChanged,
            int overtimeDeltaMinutes) {
    }

    public record CorrectionResponse(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            LocalDate workDate,
            UUID summaryId,
            UUID policyId,
            OffsetDateTime requestedClockIn,
            OffsetDateTime requestedClockOut,
            String requestedStatus,
            String reason,
            String correctionType,
            boolean absenceStatusChanged,
            int overtimeDeltaMinutes,
            UUID workflowInstanceId,
            String workflowStatus,
            String decision,
            String decisionComment,
            OffsetDateTime decidedAt,
            String decidedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String createdBy,
            String updatedBy) {

        public static CorrectionResponse from(AttendanceCorrectionRequest entity) {
            return new CorrectionResponse(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getEmployeeId(),
                    entity.getWorkDate(),
                    entity.getSummaryId(),
                    entity.getPolicyId(),
                    entity.getRequestedClockIn(),
                    entity.getRequestedClockOut(),
                    entity.getRequestedStatus(),
                    entity.getReason(),
                    entity.getCorrectionType(),
                    entity.isAbsenceStatusChanged(),
                    entity.getOvertimeDeltaMinutes(),
                    entity.getWorkflowInstanceId(),
                    entity.getWorkflowStatus(),
                    entity.getDecision(),
                    entity.getDecisionComment(),
                    entity.getDecidedAt(),
                    entity.getDecidedBy(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt(),
                    entity.getCreatedBy(),
                    entity.getUpdatedBy());
        }
    }

    public record CorrectionDecision(String decision, String comment) {
    }
}
