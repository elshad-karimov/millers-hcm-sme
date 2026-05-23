package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.CandidateSource;

public record CandidateResponse(
        UUID id,
        String candidateNo,
        String firstName,
        String lastName,
        String middleName,
        String email,
        String phone,
        CandidateSource source,
        String cvUrl,
        BigDecimal experienceYears,
        BigDecimal expectedSalary,
        String currency,
        String skills,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static CandidateResponse from(Candidate c) {
        return new CandidateResponse(
                c.getId(), c.getCandidateNo(),
                c.getFirstName(), c.getLastName(), c.getMiddleName(),
                c.getEmail(), c.getPhone(), c.getSource(), c.getCvUrl(),
                c.getExperienceYears(), c.getExpectedSalary(), c.getCurrency(),
                c.getSkills(), c.getNotes(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
