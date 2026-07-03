package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitPlanTier;

public interface BenefitPlanTierRepository extends JpaRepository<BenefitPlanTier, UUID> {

    List<BenefitPlanTier> findByPlanIdOrderByDisplayOrderAsc(UUID planId);

    List<BenefitPlanTier> findByPlanIdAndActiveTrueOrderByDisplayOrderAsc(UUID planId);

    void deleteByPlanId(UUID planId);
}
