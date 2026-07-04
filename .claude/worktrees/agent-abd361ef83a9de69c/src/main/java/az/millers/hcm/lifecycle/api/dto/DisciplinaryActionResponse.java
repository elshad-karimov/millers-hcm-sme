package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.DisciplinaryAction;
import az.millers.hcm.lifecycle.domain.DisciplinaryActionType;
import az.millers.hcm.lifecycle.domain.DisciplinaryStatus;

public record DisciplinaryActionResponse(
        UUID id,
        String actionNo,
        UUID employeeId,
        DisciplinaryActionType actionType,
        LocalDate incidentDate,
        LocalDate actionDate,
        String issuedBy,
        String description,
        DisciplinaryStatus status,
        UUID workflowInstanceId,
        UUID linkedCaseId,
        boolean appealFlag,
        String appealReason,
        String appealOutcome,
        OffsetDateTime appealedAt,
        OffsetDateTime issuedAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static DisciplinaryActionResponse from(DisciplinaryAction d) {
        return new DisciplinaryActionResponse(
                d.getId(),
                d.getActionNo(),
                d.getEmployeeId(),
                d.getActionType(),
                d.getIncidentDate(),
                d.getActionDate(),
                d.getIssuedBy(),
                d.getDescription(),
                d.getStatus(),
                d.getWorkflowInstanceId(),
                d.getLinkedCaseId(),
                d.isAppealFlag(),
                d.getAppealReason(),
                d.getAppealOutcome(),
                d.getAppealedAt(),
                d.getIssuedAt(),
                d.getClosedAt(),
                d.getCreatedAt(),
                d.getCreatedBy(),
                d.getUpdatedAt(),
                d.getUpdatedBy());
    }
}
