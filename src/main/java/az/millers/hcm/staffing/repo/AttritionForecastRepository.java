package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.AttritionForecast;

public interface AttritionForecastRepository extends JpaRepository<AttritionForecast, UUID> {

    List<AttritionForecast> findByTenantIdAndPlanIdOrderByOrgUnitIdAsc(String tenantId, UUID planId);

    void deleteByPlanId(UUID planId);
}
