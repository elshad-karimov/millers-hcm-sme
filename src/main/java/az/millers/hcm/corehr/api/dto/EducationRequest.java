package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import az.millers.hcm.corehr.domain.EducationLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EducationRequest(
        @NotNull EducationLevel educationLevel,
        @NotBlank @Size(max = 200) String institutionName,
        @Pattern(regexp = "^[A-Z]{2}$", message = "country must be an ISO 3166-1 alpha-2 code")
        String country,
        @Size(max = 200) String degree,
        @Size(max = 200) String major,
        LocalDate startDate,
        LocalDate endDate,
        @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal gpa,
        @Size(max = 4000) String notes) {
}
