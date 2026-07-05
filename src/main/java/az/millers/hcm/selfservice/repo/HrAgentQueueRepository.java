package az.millers.hcm.selfservice.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.selfservice.domain.HrAgentQueue;
import az.millers.hcm.selfservice.domain.ServiceRequestCategory;

/**
 * M438 — HR agent queue repository.
 */
public interface HrAgentQueueRepository extends JpaRepository<HrAgentQueue, UUID> {

    List<HrAgentQueue> findByTenantIdAndActiveTrue(String tenantId);

    Optional<HrAgentQueue> findByTenantIdAndRoutingCategory(String tenantId, ServiceRequestCategory category);
}
