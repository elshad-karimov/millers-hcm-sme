package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.PositionRequiredCompetency;

public interface PositionRequiredCompetencyRepository
        extends JpaRepository<PositionRequiredCompetency, UUID> {

    List<PositionRequiredCompetency> findByPositionIdOrderByMandatoryDescRequiredLevelDesc(UUID positionId);

    Optional<PositionRequiredCompetency> findByPositionIdAndCompetencyId(UUID positionId, UUID competencyId);

    List<PositionRequiredCompetency> findByCompetencyId(UUID competencyId);

    void deleteByPositionIdAndCompetencyId(UUID positionId, UUID competencyId);
}
