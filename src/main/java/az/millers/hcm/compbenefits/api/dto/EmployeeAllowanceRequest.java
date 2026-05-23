package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record EmployeeAllowanceRequest(
        @NotNull UUID employeeId,
        @NotNull UUID allowanceTypeId,
        @NotNull BigDecimal amount,
        String currency,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String note) {
}
