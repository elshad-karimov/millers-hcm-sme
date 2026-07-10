package az.millers.hcm.integration.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.integration.domain.IntegrationConfig;

@Repository
public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, UUID> {

    List<IntegrationConfig> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<IntegrationConfig> findByIdAndTenantId(UUID id, String tenantId);

    Optional<IntegrationConfig> findByCodeAndTenantId(String code, String tenantId);

    List<IntegrationConfig> findByTenantIdAndEnabledOrderByNameAsc(String tenantId, boolean enabled);
}
