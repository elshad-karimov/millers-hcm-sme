package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.OffboardingBenefitsClosure;

public interface OffboardingBenefitsClosureRepository extends JpaRepository<OffboardingBenefitsClosure, UUID> {
    List<OffboardingBenefitsClosure> findByCaseIdOrderByBenefitType(UUID caseId);
    Optional<OffboardingBenefitsClosure> findByCaseIdAndBenefitType(UUID caseId, String benefitType);
}
