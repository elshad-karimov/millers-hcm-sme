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
}
