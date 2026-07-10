package az.millers.hcm.engagement.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * M481: Grant points to employee.
 */
public record GrantPointsRequest(
    @NotNull UUID employeeId,
    @NotNull @Min(1) Integer points,
    String note,
    UUID orgUnitId
) {}
