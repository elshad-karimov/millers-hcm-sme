package az.millers.hcm.career.api.dto;

import java.util.UUID;

public record SkillGapRequest(
        UUID competencyId,
        String skillName,
        Short currentLevel,
        Short targetLevel,
        String notes
) {}
