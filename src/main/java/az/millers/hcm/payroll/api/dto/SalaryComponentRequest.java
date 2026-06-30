package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;

import az.millers.hcm.payroll.domain.CalculationMethod;
import az.millers.hcm.payroll.domain.ComponentKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SalaryComponentRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull ComponentKind kind,
        @NotNull CalculationMethod calculationMethod,
        BigDecimal defaultAmount,
        BigDecimal percentage,
        Boolean isTaxable,
        Boolean contributionExempt,
        Boolean isActive) {
}
