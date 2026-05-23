package az.millers.hcm.permission.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PermissionBalanceAdjustment(
        @NotNull UUID employeeId,
        @NotNull UUID permissionTypeId,
        @NotNull Integer year,
        @NotNull BigDecimal deltaHours,
        @NotBlank String reason) {
}
