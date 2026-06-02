package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeWorkExperience;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.domain.WorkExperienceType;

public record WorkExperienceResponse(
        UUID id,
        UUID employeeId,
        WorkExperienceType experienceType,
        String employerName,
        String industry,
        String jobTitle,
        LocalDate startDate,
        LocalDate endDate,
        String reasonForLeaving,
        /** Plaintext salary, gated to cleared roles. Null when masked. */
        BigDecimal lastSalary,
        String lastSalaryCurrency,
        String responsibilities,
        String referenceContact,
        boolean referenceVerified,
        VerificationStatus verificationStatus,
        String verifiedBy,
        OffsetDateTime verifiedAt,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static WorkExperienceResponse from(EmployeeWorkExperience e, boolean canSeeSalary) {
        BigDecimal salary = null;
        if (canSeeSalary && e.getLastSalary() != null && !e.getLastSalary().isBlank()) {
            try {
                salary = new BigDecimal(e.getLastSalary());
            } catch (NumberFormatException ex) {
                salary = null;
            }
        }
        return new WorkExperienceResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getExperienceType(),
                e.getEmployerName(),
                e.getIndustry(),
                e.getJobTitle(),
                e.getStartDate(),
                e.getEndDate(),
                e.getReasonForLeaving(),
                salary,
                e.getLastSalaryCurrency(),
                e.getResponsibilities(),
                e.getReferenceContact(),
                e.isReferenceVerified(),
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
