package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.PerfCompetencyAssessment;

public interface PerfCompetencyAssessmentRepository extends JpaRepository<PerfCompetencyAssessment, UUID> {

    List<PerfCompetencyAssessment> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);

    boolean existsByReviewIdAndCompetencyId(UUID reviewId, UUID competencyId);
}
