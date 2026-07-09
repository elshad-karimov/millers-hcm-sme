package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import az.millers.hcm.engagement.domain.EngagementActionItem;

/**
 * M479 — Engagement action item repository.
 */
public interface EngagementActionItemRepository extends JpaRepository<EngagementActionItem, UUID> {

    List<EngagementActionItem> findByPlanIdOrderByCreatedAtAsc(UUID planId);
}
