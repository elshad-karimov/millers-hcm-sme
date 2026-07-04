package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.corehr.domain.AssignmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create / update payload for an EmployeeAssignment (M75 / P2-20). Effective-dated:
 * close the prior open record with {@code closeOn(effectiveFrom - 1)} before
 * inserting a new one (the service handles this — callers POST/PUT freely).
 *
 * <p>{@code allocationPercent} defaults to 100.00 for PRIMARY assignments; for
 * non-PRIMARY the service-layer validator enforces that the sum of open
 * allocations for the employee stays ≤ 100.
 */
public record EmployeeAssignmentRequest(
        @NotNull UUID employeeId,
        @NotNull UUID positionId,
        @NotNull AssignmentType assignmentType,
        @DecimalMin(value = "0.01", message = "allocationPercent must be > 0")
        @DecimalMax(value = "100.00", message = "allocationPercent must be ≤ 100")
        BigDecimal allocationPercent,
        UUID matrixManagerId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Size(max = 4000) String notes) {
}
