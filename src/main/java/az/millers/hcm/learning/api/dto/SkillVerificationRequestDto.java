package az.millers.hcm.learning.api.dto;

import java.util.UUID;

public record SkillVerificationRequestDto(
        UUID competencyId,
        int requestedLevel,
        String evidenceNotes
) {}
