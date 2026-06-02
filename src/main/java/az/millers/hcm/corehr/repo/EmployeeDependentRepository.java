package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeDependent;

public interface EmployeeDependentRepository
        extends JpaRepository<EmployeeDependent, UUID> {

    List<EmployeeDependent> findByEmployeeIdOrderByRelationshipTypeAsc(UUID employeeId);

    /** Active dependents only — what benefit / insurance lookups consume. */
    List<EmployeeDependent> findByEmployeeIdAndActiveTrueOrderByRelationshipTypeAsc(UUID employeeId);

    long countByEmployeeIdAndActiveTrue(UUID employeeId);
}
