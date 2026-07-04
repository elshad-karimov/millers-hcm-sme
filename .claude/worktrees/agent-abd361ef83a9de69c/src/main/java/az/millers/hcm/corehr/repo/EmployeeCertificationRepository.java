package az.millers.hcm.corehr.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeCertification;

public interface EmployeeCertificationRepository
        extends JpaRepository<EmployeeCertification, UUID> {

    List<EmployeeCertification> findByEmployeeIdOrderByCertificationNameAsc(UUID employeeId);

    /** ExpiryAlertScheduler hot path. */
    List<EmployeeCertification> findByExpiryDate(LocalDate date);

    /**
     * Certifications expiring on or before {@code by} for the given scope.
     * Used by the M80 employee-management dashboard / report.
     */
    @org.springframework.data.jpa.repository.Query("""
            select c from EmployeeCertification c
            where c.expiryDate is not null
              and c.expiryDate <= :by
              and (:employeeIds is null or c.employeeId in :employeeIds)
            order by c.expiryDate asc
            """)
    List<EmployeeCertification> findExpiringBy(LocalDate by,
            java.util.Collection<UUID> employeeIds);
}
