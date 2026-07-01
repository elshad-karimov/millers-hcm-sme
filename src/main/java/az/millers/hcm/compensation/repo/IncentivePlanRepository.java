package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.IncentivePlan;

public interface IncentivePlanRepository extends JpaRepository<IncentivePlan, UUID> {

    List<IncentivePlan> findByTenantIdAndIsActiveTrue(String tenantId);

    List<IncentivePlan> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<IncentivePlan> findByTenantIdAndCode(String tenantId, String code);
}
