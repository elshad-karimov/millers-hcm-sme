package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create / update payload for a bank account (M74). When {@code id} is
 * non-null the service treats this as an update against that row; when null
 * it's a new account. Pre-M74 callers that only sent the employee-anchored
 * upsert payload still work — id null means create, and the single-account
 * default values (primary=true, split=100) preserve the old 1:1 behaviour.
 */
public record BankAccountRequest(
        UUID id,
        @NotNull UUID employeeId,
        @Size(max = 32) String bankCode,
        @Size(max = 160) String bankName,
        @Size(max = 40) String iban,
        @Size(max = 40) String accountNumber,
        /** ISO 9362 — 8 or 11 alphanumeric chars. */
        @Pattern(regexp = "^[A-Z0-9]{8}$|^[A-Z0-9]{11}$",
                 message = "swiftBic must be 8 or 11 uppercase alphanumeric characters")
        String swiftBic,
        @Size(min = 3, max = 3) String currency,
        /** 0 < pct ≤ 100. Null defaults to 100 (single-account case). */
        @DecimalMin(value = "0.01", message = "salarySplitPercent must be > 0")
        @DecimalMax(value = "100.00", message = "salarySplitPercent must be ≤ 100")
        BigDecimal salarySplitPercent,
        Boolean primary,
        Boolean active,
        @Size(max = 4000) String notes) {
}
