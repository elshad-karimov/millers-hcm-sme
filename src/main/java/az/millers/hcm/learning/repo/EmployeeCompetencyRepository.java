package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.EmployeeCompetency;

public interface EmployeeCompetencyRepository extends JpaRepository<EmployeeCompetency, UUID> {

    List<EmployeeCompetency> findByEmployeeIdOrderByAwardedAtDesc(UUID employeeId);

    List<EmployeeCompetency> findByCompetencyIdOrderByAwardedAtDesc(UUID competencyId);

    Optional<EmployeeCompetency> findByEmployeeIdAndCompetencyIdAndSourceRef(
            UUID employeeId, UUID competencyId, UUID sourceRef);
}
