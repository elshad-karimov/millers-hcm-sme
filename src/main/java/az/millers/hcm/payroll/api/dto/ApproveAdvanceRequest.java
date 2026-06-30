package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApproveAdvanceRequest(
        @NotNull @Positive BigDecimal approvedAmount,
        @NotNull int repaymentYear,
        @NotNull @Min(1) @Max(12) int repaymentMonth
) {}
