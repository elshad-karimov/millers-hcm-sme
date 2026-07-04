package az.millers.hcm.attendance.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.OvertimeRequest;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, UUID> {

    List<OvertimeRequest> findByTenantIdAndEmployeeIdOrderByWorkDateDesc(UUID tenantId, UUID employeeId);

    List<OvertimeRequest> findByTenantIdAndWorkflowStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    Optional<OvertimeRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<OvertimeRequest> findByTenantIdAndEmployeeIdAndWorkDateAndDecision(
            UUID tenantId, UUID employeeId, LocalDate workDate, String decision);
}
