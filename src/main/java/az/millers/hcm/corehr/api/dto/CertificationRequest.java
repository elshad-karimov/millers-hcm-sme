package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CertificationRequest(
        @NotBlank @Size(max = 200) String certificationName,
        @Size(max = 200) String issuingAuthority,
        @Size(max = 64) String licenseNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        UUID requiredForPositionId,
        UUID competencyId,
        @Size(max = 4000) String notes) {
}
