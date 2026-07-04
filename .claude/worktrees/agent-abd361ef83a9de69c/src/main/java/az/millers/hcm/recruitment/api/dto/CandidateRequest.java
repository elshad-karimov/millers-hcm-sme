package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;

import az.millers.hcm.recruitment.domain.CandidateSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String middleName,
        @Email @Size(max = 160) String email,
        @Size(max = 40) String phone,
        CandidateSource source,
        @Size(max = 500) String cvUrl,
        @DecimalMin("0.0") BigDecimal experienceYears,
        @DecimalMin("0.0") BigDecimal expectedSalary,
        @Size(min = 3, max = 3) String currency,
        String skills,
        String notes) {
}
