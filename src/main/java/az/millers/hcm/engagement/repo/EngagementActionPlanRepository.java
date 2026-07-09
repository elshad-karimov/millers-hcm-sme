package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import az.millers.hcm.engagement.domain.EngagementActionPlan;

/**
 * M479 — Engagement action plan repository.
 */
public interface EngagementActionPlanRepository extends JpaRepository<EngagementActionPlan, UUID> {

    List<EngagementActionPlan> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
