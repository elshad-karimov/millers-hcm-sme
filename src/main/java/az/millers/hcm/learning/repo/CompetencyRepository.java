package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.learning.domain.CompetencyCategory;

public interface CompetencyRepository extends JpaRepository<Competency, UUID> {

    List<Competency> findByActiveTrueOrderByNameAsc();

    List<Competency> findByCategoryAndActiveTrueOrderByNameAsc(CompetencyCategory category);

    boolean existsByCode(String code);
}
