package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.CommissionTier;

public interface CommissionTierRepository extends JpaRepository<CommissionTier, UUID> {

    List<CommissionTier> findByPlanIdOrderBySortOrder(UUID planId);

    void deleteByPlanId(UUID planId);
}
