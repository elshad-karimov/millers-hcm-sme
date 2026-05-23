package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.CycleStatus;
import az.millers.hcm.performance.domain.ReviewCycle;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {

    Optional<ReviewCycle> findByCode(String code);

    List<ReviewCycle> findAllByOrderByPeriodStartDesc();

    List<ReviewCycle> findByStatusOrderByPeriodStartDesc(CycleStatus status);
}
