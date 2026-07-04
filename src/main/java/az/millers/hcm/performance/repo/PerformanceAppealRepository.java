package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.PerformanceAppeal;

public interface PerformanceAppealRepository extends JpaRepository<PerformanceAppeal, UUID> {

    List<PerformanceAppeal> findByReviewIdOrderBySubmittedAtDesc(UUID reviewId);

    List<PerformanceAppeal> findByTenantIdOrderBySubmittedAtDesc(String tenantId);

    List<PerformanceAppeal> findByTenantIdAndStatusOrderBySubmittedAtDesc(String tenantId, String status);

    boolean existsByReviewIdAndStatusIn(UUID reviewId, List<String> statuses);
}
