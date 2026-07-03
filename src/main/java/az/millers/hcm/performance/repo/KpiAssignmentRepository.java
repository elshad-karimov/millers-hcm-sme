package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.KpiAssignment;

public interface KpiAssignmentRepository extends JpaRepository<KpiAssignment, UUID> {

    List<KpiAssignment> findByTenantIdAndCycleIdOrderByCreatedAtAsc(String tenantId, UUID cycleId);

    List<KpiAssignment> findByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(
            String tenantId, UUID cycleId, UUID employeeId);

    List<KpiAssignment> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    boolean existsByKpiIdAndCycleIdAndEmployeeId(UUID kpiId, UUID cycleId, UUID employeeId);
}
