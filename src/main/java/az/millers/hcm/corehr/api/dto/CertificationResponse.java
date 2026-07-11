package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeCertification;
import az.millers.hcm.corehr.domain.VerificationStatus;

public record CertificationResponse(
        UUID id,
        UUID employeeId,
        String certificationName,
        String issuingAuthority,
        /** Last 4 chars only — full plaintext available via {@link #licenseNumber} for cleared roles. */
        String licenseNumberMasked,
        String licenseNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        UUID requiredForPositionId,
        UUID competencyId,
        VerificationStatus verificationStatus,
        String verifiedBy,
        OffsetDateTime verifiedAt,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static CertificationResponse from(EmployeeCertification e, boolean canSeePlainNumber) {
        String n = e.getLicenseNumber();
        String masked = az.millers.hcm.security.PiiMasking.maskDocumentId(n);
        return new CertificationResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getCertificationName(),
                e.getIssuingAuthority(),
                masked,
                canSeePlainNumber ? n : null,
                e.getIssueDate(),
                e.getExpiryDate(),
                e.getRequiredForPositionId(),
                e.getCompetencyId(),
                e.getVerificationStatus(),
                e.getVerifiedBy(),
                e.getVerifiedAt(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getUpdatedAt(),
                e.getUpdatedBy());
    }
}
