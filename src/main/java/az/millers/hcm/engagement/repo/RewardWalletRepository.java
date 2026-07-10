package az.millers.hcm.engagement.repo;

import az.millers.hcm.engagement.domain.RewardWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * M481: Reward wallet repository.
 */
@Repository
public interface RewardWalletRepository extends JpaRepository<RewardWallet, UUID> {

    Optional<RewardWallet> findByTenantIdAndEmployeeId(String tenantId, UUID employeeId);
}
