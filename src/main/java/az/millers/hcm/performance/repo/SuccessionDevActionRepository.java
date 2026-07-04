package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.SuccessionDevAction;

public interface SuccessionDevActionRepository extends JpaRepository<SuccessionDevAction, UUID> {
    List<SuccessionDevAction> findByNominationId(UUID nominationId);
    Optional<SuccessionDevAction> findByNominationIdAndDevPlanId(UUID nominationId, UUID devPlanId);
    void deleteByNominationIdAndDevPlanId(UUID nominationId, UUID devPlanId);
}
