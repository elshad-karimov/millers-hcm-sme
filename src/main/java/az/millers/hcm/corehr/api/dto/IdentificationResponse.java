package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeIdentification;
import az.millers.hcm.corehr.domain.IdentificationDocumentType;
import az.millers.hcm.corehr.domain.VerificationStatus;

public record IdentificationResponse(
        UUID id,
        UUID employeeId,
        IdentificationDocumentType documentType,
        /** Last 4 chars only, prefixed with "…", for any role below HR_ADMIN. */
        String documentNumberMasked,
        /** Full plaintext — populated only for callers cleared to see PII. */
        String documentNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        String issuingAuthority,
        String issuingCountry,
        VerificationStatus verificationStatus,
        String verifiedBy,
        OffsetDateTime verifiedAt,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static IdentificationResponse from(EmployeeIdentification e, boolean canSeePlainNumber) {
        String number = e.getDocumentNumber();
        String masked = mask(number);
        return new IdentificationResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getDocumentType(),
                masked,
                canSeePlainNumber ? number : null,
                e.getIssueDate(),
                e.getExpiryDate(),
                e.getIssuingAuthority(),
                e.getIssuingCountry(),
                e.getVerificationStatus(),
                e.getVerifiedBy(),
                e.getVerifiedAt(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getUpdatedAt(),
                e.getUpdatedBy());
    }

    private static String mask(String n) {
        if (n == null || n.isBlank()) return null;
        if (n.length() <= 4) return "…" + n;
        return "…" + n.substring(n.length() - 4);
    }
}
