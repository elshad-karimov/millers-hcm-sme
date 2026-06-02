package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import az.millers.hcm.corehr.domain.WorkExperienceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkExperienceRequest(
        WorkExperienceType experienceType,
        @NotBlank @Size(max = 200) String employerName,
        @Size(max = 120) String industry,
        @NotBlank @Size(max = 200) String jobTitle,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 200) String reasonForLeaving,
        BigDecimal lastSalary,
        @Size(min = 3, max = 3) String lastSalaryCurrency,
        @Size(max = 8000) String responsibilities,
        @Size(max = 200) String referenceContact,
        Boolean referenceVerified,
        @Size(max = 4000) String notes) {
}
