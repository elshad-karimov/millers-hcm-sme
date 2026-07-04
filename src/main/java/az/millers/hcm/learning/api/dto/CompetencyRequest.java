package az.millers.hcm.learning.api.dto;

import az.millers.hcm.learning.domain.CompetencyCategory;
import az.millers.hcm.learning.domain.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompetencyRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull CompetencyCategory category,
        SkillType skillType,
        Boolean active) {
}
