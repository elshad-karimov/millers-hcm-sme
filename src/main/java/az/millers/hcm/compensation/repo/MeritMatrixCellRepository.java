package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.MeritMatrixCell;

public interface MeritMatrixCellRepository extends JpaRepository<MeritMatrixCell, UUID> {
    List<MeritMatrixCell> findByMatrixId(UUID matrixId);
    Optional<MeritMatrixCell> findByMatrixIdAndPerformanceBandAndRangePosition(
            UUID matrixId, String performanceBand, String rangePosition);
    void deleteByMatrixId(UUID matrixId);
}
