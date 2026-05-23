package az.millers.hcm.learning.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.learning.domain.CompetencyCategory;

public record CompetencyResponse(
        UUID id,
        String code,
        String name,
        String description,
        CompetencyCategory category,
        boolean active,
        OffsetDateTime createdAt) {

    public static CompetencyResponse from(Competency c) {
        return new CompetencyResponse(
                c.getId(), c.getCode(), c.getName(), c.getDescription(),
                c.getCategory(), c.isActive(), c.getCreatedAt());
    }
}
