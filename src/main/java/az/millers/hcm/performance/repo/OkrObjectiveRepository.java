package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.OkrObjective;

public interface OkrObjectiveRepository extends JpaRepository<OkrObjective, UUID> {

    List<OkrObjective> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<OkrObjective> findByTenantIdAndCycleIdOrderByCreatedAtDesc(String tenantId, UUID cycleId);

    List<OkrObjective> findByTenantIdAndOkrLevelOrderByCreatedAtDesc(String tenantId, String okrLevel);

    List<OkrObjective> findByTenantIdAndCycleIdAndOkrLevelOrderByCreatedAtDesc(
            String tenantId, UUID cycleId, String okrLevel);

    List<OkrObjective> findByTenantIdAndOwnerEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID ownerEmployeeId);

    boolean existsByParentId(UUID parentId);
}
