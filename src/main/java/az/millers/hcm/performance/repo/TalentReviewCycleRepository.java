package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.TalentReviewCycle;

public interface TalentReviewCycleRepository extends JpaRepository<TalentReviewCycle, UUID> {

    List<TalentReviewCycle> findByTenantIdOrderByYearDesc(String tenantId);

    List<TalentReviewCycle> findByTenantIdAndStatusOrderByYearDesc(String tenantId, String status);
}
