package az.millers.hcm.corehr.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeHealth;

public interface EmployeeHealthRepository extends JpaRepository<EmployeeHealth, UUID> {

    Optional<EmployeeHealth> findByEmployeeId(UUID employeeId);

    /** ExpiryAlertScheduler hot path. */
    List<EmployeeHealth> findByNextExamDate(LocalDate date);
}
