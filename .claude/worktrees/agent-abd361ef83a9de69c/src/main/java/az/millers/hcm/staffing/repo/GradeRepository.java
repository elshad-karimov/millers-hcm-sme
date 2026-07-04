package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.Grade;

public interface GradeRepository extends JpaRepository<Grade, UUID> {
    Optional<Grade> findByCode(String code);
    List<Grade> findByActiveTrueOrderByLevelAscNameAsc();
}
