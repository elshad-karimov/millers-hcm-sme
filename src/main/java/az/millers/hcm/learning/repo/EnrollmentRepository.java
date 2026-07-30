package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.domain.EnrollmentStatus;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('learning.enrollment_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Optional<Enrollment> findByCourseIdAndEmployeeId(UUID courseId, UUID employeeId);

    List<Enrollment> findByEmployeeIdOrderByEnrolledAtDesc(UUID employeeId);

    Page<Enrollment> findByEmployeeIdOrderByEnrolledAtDesc(UUID employeeId, Pageable pageable);

    Page<Enrollment> findByCourseIdOrderByEnrolledAtDesc(UUID courseId, Pageable pageable);

    Page<Enrollment> findByStatusOrderByEnrolledAtDesc(EnrollmentStatus status, Pageable pageable);

    Page<Enrollment> findAllByOrderByEnrolledAtDesc(Pageable pageable);
}
