package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.CareerPathStep;

public interface CareerPathStepRepository extends JpaRepository<CareerPathStep, UUID> {
    List<CareerPathStep> findByPathIdOrderByStepOrderAsc(UUID pathId);
    void deleteByPathId(UUID pathId);
}
