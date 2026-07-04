package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.DevelopmentPlan;

public interface DevelopmentPlanRepository extends JpaRepository<DevelopmentPlan, UUID> {

    List<DevelopmentPlan> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<DevelopmentPlan> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    List<DevelopmentPlan> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
}
