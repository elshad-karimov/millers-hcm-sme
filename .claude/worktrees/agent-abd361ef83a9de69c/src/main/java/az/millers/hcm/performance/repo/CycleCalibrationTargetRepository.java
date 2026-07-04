package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.CycleCalibrationTarget;
import az.millers.hcm.performance.domain.CycleCalibrationTargetId;

public interface CycleCalibrationTargetRepository
        extends JpaRepository<CycleCalibrationTarget, CycleCalibrationTargetId> {

    List<CycleCalibrationTarget> findByCycleId(UUID cycleId);

    void deleteByCycleId(UUID cycleId);
}
