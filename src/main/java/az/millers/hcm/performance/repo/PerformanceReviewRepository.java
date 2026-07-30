package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewStatus;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('performance.review_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Optional<PerformanceReview> findByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    List<PerformanceReview> findByCycleIdOrderByCreatedAtDesc(UUID cycleId);

    Page<PerformanceReview> findByCycleIdOrderByCreatedAtDesc(UUID cycleId, Pageable pageable);

    Page<PerformanceReview> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId, Pageable pageable);

    Page<PerformanceReview> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    Page<PerformanceReview> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
