package az.millers.hcm.engagement.repo;

import az.millers.hcm.engagement.domain.RewardCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M481: Reward catalog repository.
 */
@Repository
public interface RewardCatalogRepository extends JpaRepository<RewardCatalog, UUID> {

    List<RewardCatalog> findByTenantIdOrderByName(String tenantId);

    List<RewardCatalog> findByTenantIdAndActiveOrderByName(String tenantId, Boolean active);

    Optional<RewardCatalog> findByIdAndTenantId(UUID id, String tenantId);

    Optional<RewardCatalog> findByTenantIdAndCode(String tenantId, String code);
}
