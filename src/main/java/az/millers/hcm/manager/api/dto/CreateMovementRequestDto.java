package az.millers.hcm.manager.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.MovementType;

public record CreateMovementRequestDto(
        UUID employeeId,
        MovementType movementType,
        UUID proposedPositionId,
        UUID proposedOrgUnitId,
        String proposedGrade,
        BigDecimal proposedSalary,
        LocalDate effectiveDate,
        String justification
) {}
