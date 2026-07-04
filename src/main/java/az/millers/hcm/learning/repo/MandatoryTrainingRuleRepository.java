package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.MandatoryTrainingRule;

public interface MandatoryTrainingRuleRepository extends JpaRepository<MandatoryTrainingRule, UUID> {

    List<MandatoryTrainingRule> findByTenantIdOrderByNameAsc(String tenantId);

    List<MandatoryTrainingRule> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);
}
