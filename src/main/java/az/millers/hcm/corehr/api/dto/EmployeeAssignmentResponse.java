package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.AssignmentType;
import az.millers.hcm.corehr.domain.EmployeeAssignment;

public record EmployeeAssignmentResponse(
        UUID id,
        UUID employeeId,
        UUID positionId,
        AssignmentType assignmentType,
        BigDecimal allocationPercent,
        UUID matrixManagerId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String notes,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static EmployeeAssignmentResponse from(EmployeeAssignment a) {
        return new EmployeeAssignmentResponse(
                a.getId(), a.getEmployeeId(), a.getPositionId(),
                a.getAssignmentType(), a.getAllocationPercent(),
                a.getMatrixManagerId(),
                a.getEffectiveFrom(), a.getEffectiveTo(), a.getNotes(),
                a.getCreatedAt(), a.getCreatedBy(),
                a.getUpdatedAt(), a.getUpdatedBy());
    }
}
