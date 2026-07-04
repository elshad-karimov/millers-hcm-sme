package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.AttendanceCorrectionRequest;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrectionRequest, UUID> {

    List<AttendanceCorrectionRequest> findByTenantIdAndEmployeeIdOrderByWorkDateDesc(UUID tenantId, UUID employeeId);

    List<AttendanceCorrectionRequest> findByTenantIdAndWorkflowStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<AttendanceCorrectionRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<AttendanceCorrectionRequest> findByIdAndTenantId(UUID id, UUID tenantId);
}
