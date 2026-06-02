package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.BankAccount;

public record BankAccountResponse(
        UUID id,
        UUID employeeId,
        String bankCode,
        String bankName,
        /** Last-4 mask for non-cleared roles. */
        String ibanMasked,
        /** Full IBAN — only for cleared roles. */
        String iban,
        String accountNumberMasked,
        String accountNumber,
        String swiftBic,
        String currency,
        BigDecimal salarySplitPercent,
        boolean primary,
        boolean active,
        String notes,
        UUID lastChangeWorkflowId,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static BankAccountResponse from(BankAccount a, boolean canSeePlain) {
        return new BankAccountResponse(
                a.getId(),
                a.getEmployeeId(),
                a.getBankCode(),
                a.getBankName(),
                mask(a.getIban()),
                canSeePlain ? a.getIban() : null,
                mask(a.getAccountNumber()),
                canSeePlain ? a.getAccountNumber() : null,
                a.getSwiftBic(),
                a.getCurrency(),
                a.getSalarySplitPercent(),
                a.isPrimary(),
                a.isActive(),
                a.getNotes(),
                a.getLastChangeWorkflowId(),
                a.getCreatedAt(),
                a.getCreatedBy(),
                a.getUpdatedAt(),
                a.getUpdatedBy());
    }

    private static String mask(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.length() <= 4) return "…" + s;
        return "…" + s.substring(s.length() - 4);
    }
}
