package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateLoanRequest(
        @NotNull UUID employeeId,
        @NotNull @Positive BigDecimal principalAmount,
        @NotNull @Positive BigDecimal monthlyInstallment,
        @NotNull int startDeductionYear,
        @NotNull @Min(1) @Max(12) int startDeductionMonth,
        String description
) {}
