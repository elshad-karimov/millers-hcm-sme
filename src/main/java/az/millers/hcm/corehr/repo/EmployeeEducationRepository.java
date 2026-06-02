package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeEducation;

public interface EmployeeEducationRepository
        extends JpaRepository<EmployeeEducation, UUID> {

    /** Most recent graduation first (NULL end_date treated as "still in progress"). */
    List<EmployeeEducation> findByEmployeeIdOrderByEndDateDescNullsFirst(UUID employeeId);
}
