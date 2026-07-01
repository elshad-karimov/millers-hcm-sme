package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.IncentivePayout;

public interface IncentivePayoutRepository extends JpaRepository<IncentivePayout, UUID> {

    List<IncentivePayout> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<IncentivePayout> findByTenantIdAndPlanIdOrderByCreatedAtDesc(String tenantId, UUID planId);

    List<IncentivePayout> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    List<IncentivePayout> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
}
