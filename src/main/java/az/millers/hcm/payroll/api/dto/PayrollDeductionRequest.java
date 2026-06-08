package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PayrollDeductionRequest(

        @NotNull UUID employeeId,

        @NotBlank
        @Pattern(regexp = "ONE_OFF|RECURRING|GARNISHMENT|ADVANCE_RECOVERY",
                 message = "deductionType must be ONE_OFF, RECURRING, GARNISHMENT or ADVANCE_RECOVERY")
        String deductionType,

        @NotBlank String description,

        @NotNull @Positive BigDecimal amountPerPeriod,

        /** Required for ADVANCE_RECOVERY; null for other types. */
        BigDecimal totalAmount,

        @NotNull int startPeriodYear,

        @NotNull @Min(1) @Max(12) int startPeriodMonth,

        Integer endPeriodYear,

        @Min(1) @Max(12) Integer endPeriodMonth,

        String note
) {}
