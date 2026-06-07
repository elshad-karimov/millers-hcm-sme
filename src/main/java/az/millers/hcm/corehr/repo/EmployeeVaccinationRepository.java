package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeVaccination;

public interface EmployeeVaccinationRepository extends JpaRepository<EmployeeVaccination, UUID> {

    List<EmployeeVaccination> findByEmployeeIdOrderByAdministeredDateDesc(UUID employeeId);
}
