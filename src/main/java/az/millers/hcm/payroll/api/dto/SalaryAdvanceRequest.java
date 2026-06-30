package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SalaryAdvanceRequest(
        UUID employeeId,  // nullable — for HR; EMPLOYEE role always derives from context
        @NotNull @Positive BigDecimal requestedAmount,
        String reason
) {}
