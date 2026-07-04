package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.CourseCategory;
import az.millers.hcm.learning.domain.CourseStatus;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Query(value = "SELECT nextval('learning.course_no_seq')", nativeQuery = true)
    long nextNoSequence();

    boolean existsByCode(String code);

    /**
     * M252 — Phase F.4 lookup: when a position profile TRAINING item
     * carries a {@code reference_code} matching {@code course.code},
     * the grant auto-enrols the employee in the matching course.
     */
    Optional<Course> findByCode(String code);

    Page<Course> findByStatusOrderByTitleAsc(CourseStatus status, Pageable pageable);

    Page<Course> findByCategoryOrderByTitleAsc(CourseCategory category, Pageable pageable);

    Page<Course> findAllByOrderByTitleAsc(Pageable pageable);

    List<Course> findByStatusOrderByTitleAsc(CourseStatus status);

    List<Course> findByMandatoryTrueAndStatusOrderByTitleAsc(CourseStatus status);
}
