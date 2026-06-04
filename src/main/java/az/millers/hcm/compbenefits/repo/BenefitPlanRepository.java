package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.BenefitType;

public interface BenefitPlanRepository extends JpaRepository<BenefitPlan, UUID> {

    boolean existsByCode(String code);

    List<BenefitPlan> findAllByOrderByBenefitTypeAscNameAsc();

    List<BenefitPlan> findByActiveTrueOrderByBenefitTypeAscNameAsc();

    List<BenefitPlan> findByBenefitTypeOrderByNameAsc(BenefitType benefitType);
}
