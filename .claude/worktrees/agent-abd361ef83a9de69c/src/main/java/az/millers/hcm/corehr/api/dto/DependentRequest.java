package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import az.millers.hcm.corehr.domain.DependentEligibilityEndReason;
import az.millers.hcm.corehr.domain.DependentRelationship;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record DependentRequest(
        @NotNull DependentRelationship relationshipType,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String middleName,
        @Past LocalDate dateOfBirth,
        @Size(max = 16) String gender,
        @Size(max = 64) String nationalId,
        @Size(max = 40) String phone,
        @Email @Size(max = 160) String email,
        Boolean insuranceEligible,
        Boolean benefitEligible,
        Boolean active,
        // ── M135 — Section 13 eligibility termination ────────────────
        /** Both eligibility fields must be set together — or neither. */
        LocalDate eligibilityEndDate,
        DependentEligibilityEndReason eligibilityEndReason,
        @Size(max = 4000) String notes) {
}
