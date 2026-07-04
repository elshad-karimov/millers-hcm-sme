package az.millers.hcm.manager.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.EmployeeMovementRequest;
import az.millers.hcm.lifecycle.domain.MovementRequestStatus;
import az.millers.hcm.lifecycle.domain.MovementType;

public record MovementRequestDto(
        UUID id,
        String requestNo,
        UUID employeeId,
        MovementType movementType,
        UUID proposedPositionId,
        UUID proposedOrgUnitId,
        String proposedGrade,
        BigDecimal proposedSalary,
        LocalDate effectiveDate,
        String justification,
        MovementRequestStatus status,
        UUID workflowInstanceId,
        String requestedBy
) {
    public static MovementRequestDto from(EmployeeMovementRequest entity, boolean isHR) {
        return new MovementRequestDto(
                entity.getId(),
                entity.getRequestNo(),
                entity.getEmployeeId(),
                entity.getMovementType(),
                entity.getProposedPositionId(),
                entity.getProposedOrgUnitId(),
                entity.getProposedGrade(),
                isHR ? entity.getProposedSalary() : null, // Salary visible only to HR
                entity.getEffectiveDate(),
                entity.getJustification(),
                entity.getStatus(),
                entity.getWorkflowInstanceId(),
                entity.getRequestedBy()
        );
    }
}
