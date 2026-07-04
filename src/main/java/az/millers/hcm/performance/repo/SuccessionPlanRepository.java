package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.SuccessionPlan;

public interface SuccessionPlanRepository extends JpaRepository<SuccessionPlan, UUID> {

    List<SuccessionPlan> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<SuccessionPlan> findByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, String status);

    List<SuccessionPlan> findByCriticalPositionIdOrderByUpdatedAtDesc(UUID criticalPositionId);

    Optional<SuccessionPlan> findByWorkflowInstanceId(UUID workflowInstanceId);
}
