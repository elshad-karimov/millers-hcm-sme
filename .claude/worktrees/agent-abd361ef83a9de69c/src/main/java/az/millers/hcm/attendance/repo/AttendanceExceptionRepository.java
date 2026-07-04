package az.millers.hcm.attendance.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.AttendanceException;

public interface AttendanceExceptionRepository extends JpaRepository<AttendanceException, UUID> {

    List<AttendanceException> findByTenantIdOrderByWorkDateDescCreatedAtDesc(UUID tenantId);

    List<AttendanceException> findByTenantIdAndEmployeeIdOrderByWorkDateDesc(UUID tenantId, UUID employeeId);

    List<AttendanceException> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    Optional<AttendanceException> findByTenantIdAndEmployeeIdAndWorkDateAndExceptionType(
            UUID tenantId, UUID employeeId, LocalDate workDate, String exceptionType);

    Optional<AttendanceException> findByIdAndTenantId(UUID id, UUID tenantId);
}
