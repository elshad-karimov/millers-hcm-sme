package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.Kpi;

public interface KpiRepository extends JpaRepository<Kpi, UUID> {

    List<Kpi> findByTenantIdOrderByKpiNameAsc(String tenantId);

    List<Kpi> findByTenantIdAndActiveTrueOrderByKpiNameAsc(String tenantId);

    boolean existsByTenantIdAndKpiCode(String tenantId, String kpiCode);
}
