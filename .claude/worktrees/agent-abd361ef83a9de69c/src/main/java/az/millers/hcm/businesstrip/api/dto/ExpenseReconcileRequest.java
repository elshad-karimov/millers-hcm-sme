package az.millers.hcm.businesstrip.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExpenseReconcileRequest(
        @NotNull @DecimalMin("0.0") BigDecimal actualExpense,
        @DecimalMin("0.0") BigDecimal paidAdvance,
        @NotBlank String reason) {
}
