package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.PerformancePip;

public interface PerformancePipRepository extends JpaRepository<PerformancePip, UUID> {

    List<PerformancePip> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<PerformancePip> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);

    List<PerformancePip> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    boolean existsByEmployeeIdAndStatusIn(UUID employeeId, List<String> statuses);
}
