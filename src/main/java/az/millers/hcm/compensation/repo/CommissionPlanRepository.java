package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.CommissionPlan;

public interface CommissionPlanRepository extends JpaRepository<CommissionPlan, UUID> {

    List<CommissionPlan> findByTenantIdAndIsActiveTrue(String tenantId);

    List<CommissionPlan> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<CommissionPlan> findByTenantIdAndCode(String tenantId, String code);
}
