package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeaveBalanceAdjustment(
        @NotNull UUID employeeId,
        @NotNull UUID leaveTypeId,
        @NotNull Integer year,
        @NotNull BigDecimal deltaDays,
        @NotBlank String reason) {
}
