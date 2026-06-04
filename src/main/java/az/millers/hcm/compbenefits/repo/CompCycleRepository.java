package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.CompCycle;
import az.millers.hcm.compbenefits.domain.CompCycleStatus;

public interface CompCycleRepository extends JpaRepository<CompCycle, UUID> {

    boolean existsByCode(String code);

    List<CompCycle> findAllByOrderByCreatedAtDesc();

    List<CompCycle> findByStatusOrderByCreatedAtDesc(CompCycleStatus status);
}
