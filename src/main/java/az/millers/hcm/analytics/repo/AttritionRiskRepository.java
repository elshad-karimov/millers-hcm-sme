package az.millers.hcm.analytics.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import az.millers.hcm.analytics.domain.AttritionRisk;

/**
 * M476 — Attrition risk repository.
 */
public interface AttritionRiskRepository extends JpaRepository<AttritionRisk, UUID> {

    List<AttritionRisk> findByTenantIdOrderByScoreDesc(String tenantId);

    Optional<AttritionRisk> findByTenantIdAndEmployeeId(String tenantId, UUID employeeId);
}
