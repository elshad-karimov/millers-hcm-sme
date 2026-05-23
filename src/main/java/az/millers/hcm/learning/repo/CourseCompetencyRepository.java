package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.CourseCompetency;
import az.millers.hcm.learning.domain.CourseCompetencyId;

public interface CourseCompetencyRepository extends JpaRepository<CourseCompetency, CourseCompetencyId> {

    List<CourseCompetency> findByCourseId(UUID courseId);

    void deleteByCourseId(UUID courseId);
}
