package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ResignationRequest;
import az.millers.hcm.lifecycle.domain.ResignationStatus;

public record ResignationResponse(
        UUID id,
        String resignationNo,
        UUID employeeId,
        LocalDate resignationDate,
        LocalDate proposedLastWorkingDate,
        LocalDate calculatedLastWorkingDate,
        LocalDate approvedLastWorkingDate,
        Integer noticeDaysCalculated,
        String reasonText,
        String comments,
        ResignationStatus status,
        UUID workflowInstanceId,
        String withdrawalReason,
        OffsetDateTime withdrawnAt,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt
) {
    public static ResignationResponse from(ResignationRequest r) {
        return new ResignationResponse(
                r.getId(), r.getResignationNo(), r.getEmployeeId(),
                r.getResignationDate(), r.getProposedLastWorkingDate(),
                r.getCalculatedLastWorkingDate(), r.getApprovedLastWorkingDate(),
                r.getNoticeDaysCalculated(), r.getReasonText(), r.getComments(),
                r.getStatus(), r.getWorkflowInstanceId(),
                r.getWithdrawalReason(), r.getWithdrawnAt(),
                r.getCreatedAt(), r.getCreatedBy(), r.getUpdatedAt());
    }
}
