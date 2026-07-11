package az.millers.hcm.organization.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.organization.domain.LegalEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * M140 — wire DTOs for the Legal Entity master.
 */
public final class LegalEntityDtos {

    private LegalEntityDtos() {}

    public record LegalEntityRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 240) String name,
            @Size(max = 80)  String registrationNumber,
            @Size(max = 80)  String taxId,
            @Size(max = 80)  String socialInsuranceRegNumber,
            @Size(max = 500) String legalAddress,
            @Pattern(regexp = "^[A-Z]{2}$", message = "country must be ISO 3166-1 alpha-2")
            String country,
            @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be ISO 4217 alpha-3")
            String currency,
            @Size(max = 40)  String fiscalCalendar,
            @Size(max = 160) String payrollBankName,
            @Size(max = 64)  String payrollBankAccount,
            @Pattern(regexp = "^[A-Z0-9]{8}([A-Z0-9]{3})?$|^$",
                     message = "swift must be 8 or 11 chars (ISO 9362)")
            String payrollBankSwift,
            @Size(max = 60)  String defaultCostCentreCode,
            @Size(max = 120) String chartOfAccountsRef,
            @Size(max = 160) String legalRepresentativeName,
            @Size(max = 120) String legalRepresentativeTitle,
            @Size(max = 500) String companySealUrl,
            Boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 4000) String notes) {}

    public record LegalEntityResponse(
            UUID id,
            String code,
            String name,
            String registrationNumber,
            String taxId,
            String socialInsuranceRegNumber,
            String legalAddress,
            String country,
            String currency,
            String fiscalCalendar,
            String payrollBankName,
            /** Last-4 mask only — plaintext gated by role. */
            String payrollBankAccountMasked,
            /** Plaintext — only when caller has SYSTEM_ADMIN / HR_ADMIN. */
            String payrollBankAccount,
            String payrollBankSwift,
            String defaultCostCentreCode,
            String chartOfAccountsRef,
            String legalRepresentativeName,
            String legalRepresentativeTitle,
            String companySealUrl,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String notes,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static LegalEntityResponse from(LegalEntity e, boolean canSeeBankPlain) {
            String acct = e.getPayrollBankAccount();
            String masked = az.millers.hcm.security.PiiMasking.maskAccountNumber(acct);
            return new LegalEntityResponse(
                    e.getId(), e.getCode(), e.getName(),
                    e.getRegistrationNumber(), e.getTaxId(), e.getSocialInsuranceRegNumber(),
                    e.getLegalAddress(), e.getCountry(), e.getCurrency(), e.getFiscalCalendar(),
                    e.getPayrollBankName(),
                    masked,
                    canSeeBankPlain ? acct : null,
                    e.getPayrollBankSwift(),
                    e.getDefaultCostCentreCode(), e.getChartOfAccountsRef(),
                    e.getLegalRepresentativeName(), e.getLegalRepresentativeTitle(),
                    e.getCompanySealUrl(),
                    e.isActive(), e.getEffectiveFrom(), e.getEffectiveTo(), e.getNotes(),
                    e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy());
        }
    }
}
