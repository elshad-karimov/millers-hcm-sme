package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.PositionCompetencyRequirement;
import az.millers.hcm.learning.domain.PositionCompetencyRequirementId;

public interface PositionCompetencyRequirementRepository
        extends JpaRepository<PositionCompetencyRequirement, PositionCompetencyRequirementId> {

    List<PositionCompetencyRequirement> findByPositionIdOrderByCompetencyNameAsc(UUID positionId);

    Optional<PositionCompetencyRequirement> findByPositionIdAndCompetencyId(
            UUID positionId, UUID competencyId);

    void deleteByPositionIdAndCompetencyId(UUID positionId, UUID competencyId);
}
