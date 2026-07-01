package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.CommissionPayout;

public interface CommissionPayoutRepository extends JpaRepository<CommissionPayout, UUID> {

    List<CommissionPayout> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<CommissionPayout> findByTenantIdAndPlanIdOrderByCreatedAtDesc(String tenantId, UUID planId);

    List<CommissionPayout> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    List<CommissionPayout> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
}
