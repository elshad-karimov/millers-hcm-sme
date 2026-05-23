package az.millers.hcm.payroll.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.BankAccount;

public record BankAccountResponse(
        UUID id,
        UUID employeeId,
        String bankCode,
        String bankName,
        String iban,
        String accountNumber,
        String currency,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static BankAccountResponse from(BankAccount a) {
        return new BankAccountResponse(
                a.getId(), a.getEmployeeId(),
                a.getBankCode(), a.getBankName(),
                a.getIban(), a.getAccountNumber(),
                a.getCurrency(), a.isActive(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
