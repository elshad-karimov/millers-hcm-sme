package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeEmergencyContact;

public interface EmployeeEmergencyContactRepository
        extends JpaRepository<EmployeeEmergencyContact, UUID> {

    List<EmployeeEmergencyContact> findByEmployeeIdOrderByPriorityOrderAsc(UUID employeeId);

    /** Used to clear the primary flag before promoting another contact. */
    Optional<EmployeeEmergencyContact> findByEmployeeIdAndPrimaryTrue(UUID employeeId);
}
