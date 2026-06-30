package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ComponentAssignmentRequest(
        @NotNull UUID componentId,
        BigDecimal amountOverride,
        @NotNull LocalDate effectiveFrom,
        String reason) {
}
