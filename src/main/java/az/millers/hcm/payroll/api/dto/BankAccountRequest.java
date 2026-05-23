package az.millers.hcm.payroll.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BankAccountRequest(
        @NotNull UUID employeeId,
        @Size(max = 32) String bankCode,
        @Size(max = 160) String bankName,
        @Size(max = 40) String iban,
        @Size(max = 40) String accountNumber,
        @Size(min = 3, max = 3) String currency,
        Boolean active) {
}
