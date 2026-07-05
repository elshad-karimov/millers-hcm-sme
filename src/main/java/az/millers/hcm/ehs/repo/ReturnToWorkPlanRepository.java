package az.millers.hcm.ehs.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.ehs.domain.ReturnToWorkPlan;
import az.millers.hcm.ehs.domain.ReturnToWorkStatus;

public interface ReturnToWorkPlanRepository extends JpaRepository<ReturnToWorkPlan, UUID> {

    Optional<ReturnToWorkPlan> findByIdAndTenantId(UUID id, String tenantId);

    List<ReturnToWorkPlan> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ReturnToWorkPlan> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, ReturnToWorkStatus status);

    List<ReturnToWorkPlan> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);
}
