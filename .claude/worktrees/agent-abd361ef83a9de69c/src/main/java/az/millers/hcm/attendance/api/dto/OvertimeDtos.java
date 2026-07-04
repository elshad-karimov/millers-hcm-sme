package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.OvertimeRequest;

public class OvertimeDtos {

    public record OvertimeRequestDto(
            UUID employeeId,
            LocalDate workDate,
            UUID summaryId,
            OffsetDateTime otStart,
            OffsetDateTime otEnd,
            String reason,
            boolean preApproved) {
    }

    public record OvertimeResponse(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            LocalDate workDate,
            UUID summaryId,
            OffsetDateTime otStart,
            OffsetDateTime otEnd,
            int requestedMinutes,
            String reason,
            boolean preApproved,
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

        public static OvertimeResponse from(OvertimeRequest entity) {
            return new OvertimeResponse(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getEmployeeId(),
                    entity.getWorkDate(),
                    entity.getSummaryId(),
                    entity.getOtStart(),
                    entity.getOtEnd(),
                    entity.getRequestedMinutes(),
                    entity.getReason(),
                    entity.isPreApproved(),
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

    public record OvertimeDecision(String decision, String comment) {
    }
}
