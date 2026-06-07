package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.DependentEligibilityEndReason;
import az.millers.hcm.corehr.domain.DependentRelationship;
import az.millers.hcm.corehr.domain.EmployeeDependent;

public record DependentResponse(
        UUID id,
        UUID employeeId,
        DependentRelationship relationshipType,
        String firstName,
        String lastName,
        String middleName,
        LocalDate dateOfBirth,
        String gender,
        /** Last-4 mask. */
        String nationalIdMasked,
        /** Plaintext — only for cleared roles. */
        String nationalId,
        String phone,
        String email,
        boolean insuranceEligible,
        boolean benefitEligible,
        boolean active,
        /** M135 — when eligibility ended (null = still eligible). */
        LocalDate eligibilityEndDate,
        DependentEligibilityEndReason eligibilityEndReason,
        /**
         * Derived helper — {@code true} iff eligibility termination is
         * recorded AND it's ≤ today. Lets the SPA print "no longer
         * eligible" badges without re-running date math.
         */
        boolean currentlyEligible,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static DependentResponse from(EmployeeDependent d, boolean canSeePlainNationalId) {
        String n = d.getNationalId();
        String masked = (n == null || n.isBlank())
                ? null
                : (n.length() <= 4 ? "…" + n : "…" + n.substring(n.length() - 4));
        LocalDate end = d.getEligibilityEndDate();
        boolean stillEligible = d.isActive()
                && (end == null || end.isAfter(LocalDate.now()));
        return new DependentResponse(
                d.getId(),
                d.getEmployeeId(),
                d.getRelationshipType(),
                d.getFirstName(),
                d.getLastName(),
                d.getMiddleName(),
                d.getDateOfBirth(),
                d.getGender(),
                masked,
                canSeePlainNationalId ? n : null,
                d.getPhone(),
                d.getEmail(),
                d.isInsuranceEligible(),
                d.isBenefitEligible(),
                d.isActive(),
                end,
                d.getEligibilityEndReason(),
                stillEligible,
                d.getNotes(),
                d.getCreatedAt(),
                d.getCreatedBy(),
                d.getUpdatedAt(),
                d.getUpdatedBy());
    }
}
