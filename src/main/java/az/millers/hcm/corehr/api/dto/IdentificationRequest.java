package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import az.millers.hcm.corehr.domain.IdentificationDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IdentificationRequest(
        @NotNull IdentificationDocumentType documentType,
        @NotBlank @Size(max = 64) String documentNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        @Size(max = 160) String issuingAuthority,
        @Pattern(regexp = "^[A-Z]{2}$", message = "issuingCountry must be an ISO 3166-1 alpha-2 code")
        String issuingCountry,
        @Size(max = 4000) String notes) {
}
