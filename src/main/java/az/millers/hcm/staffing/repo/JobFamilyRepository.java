package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.JobFamily;

public interface JobFamilyRepository extends JpaRepository<JobFamily, UUID> {
    Optional<JobFamily> findByCode(String code);
    List<JobFamily> findByActiveTrueOrderByNameAsc();
}
