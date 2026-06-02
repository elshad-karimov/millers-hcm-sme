package az.millers.hcm.corehr.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.PersonalInfoChangeRequest;
import az.millers.hcm.corehr.domain.PersonalInfoChangeStatus;

public record PersonalInfoChangeResponse(
        UUID id, String requestNo, UUID employeeId,
        String fieldKey, String oldValue, String newValue, String reason,
        PersonalInfoChangeStatus status, UUID workflowInstanceId,
        OffsetDateTime submittedAt, String submittedBy,
        OffsetDateTime decidedAt, String decidedBy, String decisionComment,
        OffsetDateTime appliedAt,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static PersonalInfoChangeResponse from(PersonalInfoChangeRequest r) {
        return new PersonalInfoChangeResponse(
                r.getId(), r.getRequestNo(), r.getEmployeeId(),
                r.getFieldKey(), r.getOldValue(), r.getNewValue(), r.getReason(),
                r.getStatus(), r.getWorkflowInstanceId(),
                r.getSubmittedAt(), r.getSubmittedBy(),
                r.getDecidedAt(), r.getDecidedBy(), r.getDecisionComment(),
                r.getAppliedAt(),
                r.getCreatedAt(), r.getCreatedBy(),
                r.getUpdatedAt(), r.getUpdatedBy());
    }
}
