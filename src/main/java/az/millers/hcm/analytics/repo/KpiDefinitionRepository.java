package az.millers.hcm.analytics.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import az.millers.hcm.analytics.domain.KpiDefinition;

/**
 * M473 — KPI definition repository.
 */
public interface KpiDefinitionRepository extends JpaRepository<KpiDefinition, UUID> {

    List<KpiDefinition> findByTenantIdAndActiveOrderByCategory(String tenantId, Boolean active);

    Optional<KpiDefinition> findByTenantIdAndCode(String tenantId, String code);
}
