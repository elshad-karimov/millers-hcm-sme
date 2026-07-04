package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import az.millers.hcm.payroll.domain.BonusType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AddBonusRequest(
        @NotNull UUID employeeId,
        @NotNull BonusType bonusType,
        @NotNull @DecimalMin("0.0") BigDecimal amount,
        String note) {
}
