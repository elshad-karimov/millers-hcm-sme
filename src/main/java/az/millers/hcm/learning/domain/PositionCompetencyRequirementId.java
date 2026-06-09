package az.millers.hcm.learning.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite PK for {@link PositionCompetencyRequirement}. */
public class PositionCompetencyRequirementId implements Serializable {

    private UUID positionId;
    // Must match the field name in the entity (not the column name).
    private UUID competency;

    public PositionCompetencyRequirementId() {}

    public PositionCompetencyRequirementId(UUID positionId, UUID competency) {
        this.positionId = positionId;
        this.competency = competency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PositionCompetencyRequirementId that)) return false;
        return Objects.equals(positionId, that.positionId)
                && Objects.equals(competency, that.competency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positionId, competency);
    }
}
