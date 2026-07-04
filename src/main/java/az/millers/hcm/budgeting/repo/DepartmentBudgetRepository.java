package az.millers.hcm.budgeting.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.budgeting.domain.DepartmentBudget;
import az.millers.hcm.budgeting.domain.DepartmentBudgetStatus;

public interface DepartmentBudgetRepository extends JpaRepository<DepartmentBudget, UUID> {

    List<DepartmentBudget> findByCycleIdOrderByOrgUnitId(UUID cycleId);

    Optional<DepartmentBudget> findByCycleIdAndOrgUnitId(UUID cycleId, UUID orgUnitId);

    List<DepartmentBudget> findByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, DepartmentBudgetStatus status);
}
