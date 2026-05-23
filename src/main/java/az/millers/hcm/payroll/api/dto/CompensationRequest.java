package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompensationRequest(
        @NotNull UUID employeeId,
        @NotNull @DecimalMin("0.0") BigDecimal monthlyBaseSalary,
        @Size(min = 3, max = 3) String currency,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String reason) {
}
