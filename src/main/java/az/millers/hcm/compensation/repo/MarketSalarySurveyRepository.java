package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.MarketSalarySurvey;

public interface MarketSalarySurveyRepository extends JpaRepository<MarketSalarySurvey, UUID> {

    List<MarketSalarySurvey> findByTenantIdOrderBySurveyYearDesc(String tenantId);

    Optional<MarketSalarySurvey> findFirstByTenantIdOrderBySurveyYearDesc(String tenantId);
}
