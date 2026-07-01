package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.compensation.domain.SalaryChangeRequest;
import az.millers.hcm.compensation.domain.SalaryChangeStatus;

@Repository
public interface SalaryChangeRequestRepository extends JpaRepository<SalaryChangeRequest, UUID> {

    List<SalaryChangeRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    List<SalaryChangeRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, SalaryChangeStatus status);

    List<SalaryChangeRequest> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<SalaryChangeRequest> findByWorkflowInstanceId(UUID workflowInstanceId);
}
