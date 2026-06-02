package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EducationLevel;
import az.millers.hcm.corehr.domain.EmployeeEducation;
import az.millers.hcm.corehr.domain.VerificationStatus;

public record EducationResponse(
        UUID id,
        UUID employeeId,
        EducationLevel educationLevel,
        String institutionName,
        String country,
        String degree,
        String major,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal gpa,
        VerificationStatus verificationStatus,
        String verifiedBy,
        OffsetDateTime verifiedAt,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static EducationResponse from(EmployeeEducation e) {
        return new EducationResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getEducationLevel(),
                e.getInstitutionName(),
                e.getCountry(),
                e.getDegree(),
                e.getMajor(),
                e.getStartDate(),
                e.getEndDate(),
                e.getGpa(),
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
