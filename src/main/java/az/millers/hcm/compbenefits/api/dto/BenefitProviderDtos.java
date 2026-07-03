package az.millers.hcm.compbenefits.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BenefitProvider;
import az.millers.hcm.compbenefits.domain.BenefitProviderType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs for the benefit provider / vendor master (HCM_11 M374). */
public final class BenefitProviderDtos {

    private BenefitProviderDtos() {}

    public record ProviderRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull BenefitProviderType providerType,
            String contactName,
            @Email String contactEmail,
            String contactPhone,
            String website,
            String contractNo,
            LocalDate contractStart,
            LocalDate contractEnd,
            String notes,
            Boolean active) {
    }

    public record ProviderResponse(
            UUID id,
            String code,
            String name,
            BenefitProviderType providerType,
            String contactName,
            String contactEmail,
            String contactPhone,
            String website,
            String contractNo,
            LocalDate contractStart,
            LocalDate contractEnd,
            String notes,
            boolean active,
            OffsetDateTime createdAt) {

        public static ProviderResponse from(BenefitProvider p) {
            return new ProviderResponse(
                    p.getId(), p.getCode(), p.getName(), p.getProviderType(),
                    p.getContactName(), p.getContactEmail(), p.getContactPhone(),
                    p.getWebsite(), p.getContractNo(), p.getContractStart(), p.getContractEnd(),
                    p.getNotes(), p.isActive(), p.getCreatedAt());
        }
    }
}
