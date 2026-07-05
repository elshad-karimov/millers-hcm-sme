package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.RiskAssessment;
import az.millers.hcm.ehs.domain.RiskAssessmentStatus;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

    Optional<RiskAssessment> findByIdAndTenantId(UUID id, String tenantId);

    List<RiskAssessment> findByTenantIdOrderByRiskScoreDesc(String tenantId);

    List<RiskAssessment> findByTenantIdAndStatusOrderByRiskScoreDesc(String tenantId, RiskAssessmentStatus status);

    List<RiskAssessment> findByTenantIdAndRiskScoreGreaterThanEqualOrderByRiskScoreDesc(String tenantId, int minScore);
}
