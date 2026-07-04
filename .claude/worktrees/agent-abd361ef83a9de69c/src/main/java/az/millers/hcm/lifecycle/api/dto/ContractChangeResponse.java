package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ChangeType;
import az.millers.hcm.lifecycle.domain.ContractChange;
import az.millers.hcm.lifecycle.domain.ContractChangeStatus;

public record ContractChangeResponse(
        UUID id,
        String changeNo,
        UUID employeeId,
        ChangeType changeType,
        LocalDate effectiveDate,
        String reason,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        ContractChangeStatus status,
        UUID workflowInstanceId,
        OffsetDateTime appliedAt,
        String appliedBy,
        String attachmentUrls,
        String note,
        OffsetDateTime createdAt,
        String createdBy) {

    public static ContractChangeResponse from(ContractChange c) {
        return new ContractChangeResponse(
                c.getId(),
                c.getChangeNo(),
                c.getEmployeeId(),
                c.getChangeType(),
                c.getEffectiveDate(),
                c.getReason(),
                c.getOldValue(),
                c.getNewValue(),
                c.getStatus(),
                c.getWorkflowInstanceId(),
                c.getAppliedAt(),
                c.getAppliedBy(),
                c.getAttachmentUrls(),
                c.getNote(),
                c.getCreatedAt(),
                c.getCreatedBy());
    }
}
