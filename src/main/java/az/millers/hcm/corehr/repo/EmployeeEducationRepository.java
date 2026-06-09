package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.corehr.domain.EmployeeEducation;

public interface EmployeeEducationRepository
        extends JpaRepository<EmployeeEducation, UUID> {

    /** Most recent graduation first (NULL end_date treated as "still in progress"). */
    @Query("SELECT e FROM EmployeeEducation e WHERE e.employeeId = :employeeId ORDER BY e.endDate DESC NULLS FIRST")
    List<EmployeeEducation> findByEmployeeIdOrderByEndDateDescNullsFirst(@Param("employeeId") UUID employeeId);
}
