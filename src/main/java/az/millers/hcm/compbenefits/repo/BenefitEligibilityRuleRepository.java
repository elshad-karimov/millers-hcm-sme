package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitEligibilityRule;

public interface BenefitEligibilityRuleRepository extends JpaRepository<BenefitEligibilityRule, UUID> {

    List<BenefitEligibilityRule> findByPlanIdOrderByCreatedAtAsc(UUID planId);

    List<BenefitEligibilityRule> findByPlanIdAndActiveTrueOrderByCreatedAtAsc(UUID planId);

    void deleteByPlanId(UUID planId);
}
