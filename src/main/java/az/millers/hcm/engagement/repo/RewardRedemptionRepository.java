package az.millers.hcm.engagement.repo;

import az.millers.hcm.engagement.domain.RewardRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M481: Reward redemption repository.
 */
@Repository
public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, UUID> {

    List<RewardRedemption> findByTenantIdOrderByRequestedAtDesc(String tenantId);

    List<RewardRedemption> findByTenantIdAndStatusOrderByRequestedAtDesc(String tenantId, String status);

    List<RewardRedemption> findByTenantIdAndEmployeeIdOrderByRequestedAtDesc(String tenantId, UUID employeeId);

    Optional<RewardRedemption> findByIdAndTenantId(UUID id, String tenantId);
}
