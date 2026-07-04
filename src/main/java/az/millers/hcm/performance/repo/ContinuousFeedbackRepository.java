package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.ContinuousFeedback;

public interface ContinuousFeedbackRepository extends JpaRepository<ContinuousFeedback, UUID> {

    List<ContinuousFeedback> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);
}
