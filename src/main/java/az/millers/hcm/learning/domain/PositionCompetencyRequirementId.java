package az.millers.hcm.learning.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for {@link PositionCompetencyRequirement}. */
public class PositionCompetencyRequirementId implements Serializable {

    private UUID positionId;
    private UUID competencyId;

    public PositionCompetencyRequirementId() {}

    public PositionCompetencyRequirementId(UUID positionId, UUID competencyId) {
        this.positionId = positionId;
        this.competencyId = competencyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PositionCompetencyRequirementId that)) return false;
        return Objects.equals(positionId, that.positionId)
                && Objects.equals(competencyId, that.competencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positionId, competencyId);
    }
}
