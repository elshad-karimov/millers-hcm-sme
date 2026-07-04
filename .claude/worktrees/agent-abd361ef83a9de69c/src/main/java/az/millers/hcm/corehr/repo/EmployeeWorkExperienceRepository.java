package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeWorkExperience;

public interface EmployeeWorkExperienceRepository
        extends JpaRepository<EmployeeWorkExperience, UUID> {

    /** Most recent job first. Past employers + internal moves both surface here. */
    List<EmployeeWorkExperience> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);
}
