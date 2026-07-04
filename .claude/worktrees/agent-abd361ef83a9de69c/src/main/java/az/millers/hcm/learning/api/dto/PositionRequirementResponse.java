package az.millers.hcm.learning.api.dto;

import java.util.UUID;

import az.millers.hcm.learning.domain.PositionCompetencyRequirement;

public record PositionRequirementResponse(
        UUID positionId,
        UUID competencyId,
        String competencyCode,
        String competencyName,
        String competencyCategory,
        int requiredProficiency,
        String notes
) {
    public static PositionRequirementResponse from(PositionCompetencyRequirement r) {
        return new PositionRequirementResponse(
                r.getPositionId(),
                r.getCompetency().getId(),
                r.getCompetency().getCode(),
                r.getCompetency().getName(),
                r.getCompetency().getCategory().name(),
                r.getRequiredProficiency(),
                r.getNotes()
        );
    }
}
