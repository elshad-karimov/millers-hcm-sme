package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.LearningPathCourse;

public interface LearningPathCourseRepository extends JpaRepository<LearningPathCourse, UUID> {

    List<LearningPathCourse> findByPathIdOrderByStepOrderAsc(UUID pathId);

    /** All path-steps that reference the given course (used by the path-advance engine). */
    List<LearningPathCourse> findByCourseId(UUID courseId);

    Optional<LearningPathCourse> findByPathIdAndStepOrder(UUID pathId, int stepOrder);
}
