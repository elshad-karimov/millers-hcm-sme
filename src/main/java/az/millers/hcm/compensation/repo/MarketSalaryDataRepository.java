package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.MarketSalaryData;

public interface MarketSalaryDataRepository extends JpaRepository<MarketSalaryData, UUID> {

    List<MarketSalaryData> findByTenantIdAndSurveyIdOrderByGradeCodeAscJobCodeAsc(String tenantId, UUID surveyId);

    Optional<MarketSalaryData> findFirstByTenantIdAndSurveyIdAndGradeCode(String tenantId, UUID surveyId, String gradeCode);

    Optional<MarketSalaryData> findFirstByTenantIdAndSurveyIdAndJobCode(String tenantId, UUID surveyId, String jobCode);

    void deleteBySurveyId(UUID surveyId);
}
