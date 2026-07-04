package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.JobFunction;

public interface JobFunctionRepository extends JpaRepository<JobFunction, UUID> {
    Optional<JobFunction> findByCode(String code);
    List<JobFunction> findByActiveTrueOrderByNameAsc();
    List<JobFunction> findByJobFamilyIdAndActiveTrueOrderByNameAsc(UUID jobFamilyId);
}
