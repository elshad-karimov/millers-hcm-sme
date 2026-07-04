package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.GoalType;

public interface GoalTypeRepository extends JpaRepository<GoalType, UUID> {

    List<GoalType> findByTenantIdOrderBySortOrderAsc(String tenantId);

    List<GoalType> findByTenantIdAndActiveTrueOrderBySortOrderAsc(String tenantId);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
