package az.millers.hcm.engagement.repo;

import az.millers.hcm.engagement.domain.RewardBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M481: Reward budget repository.
 */
@Repository
public interface RewardBudgetRepository extends JpaRepository<RewardBudget, UUID> {

    List<RewardBudget> findByTenantIdAndYearOrderByOrgUnitId(String tenantId, Integer year);

    Optional<RewardBudget> findByTenantIdAndYearAndOrgUnitId(String tenantId, Integer year, UUID orgUnitId);

    Optional<RewardBudget> findByIdAndTenantId(UUID id, String tenantId);
}
