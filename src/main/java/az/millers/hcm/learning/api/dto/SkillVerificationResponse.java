package az.millers.hcm.learning.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.learning.domain.SkillVerificationRequest;

public record SkillVerificationResponse(
        UUID id,
        UUID employeeId,
        UUID competencyId,
        int requestedLevel,
        String evidenceNotes,
        String status,
        UUID verifiedByEmployeeId,
        OffsetDateTime verifiedAt,
        String verificationNotes,
        OffsetDateTime createdAt
) {
    public static SkillVerificationResponse from(SkillVerificationRequest r) {
        return new SkillVerificationResponse(
                r.getId(),
                r.getEmployeeId(),
                r.getCompetencyId(),
                r.getRequestedLevel(),
                r.getEvidenceNotes(),
                r.getStatus().name(),
                r.getVerifiedByEmployeeId(),
                r.getVerifiedAt(),
                r.getVerificationNotes(),
                r.getCreatedAt()
        );
    }
}
