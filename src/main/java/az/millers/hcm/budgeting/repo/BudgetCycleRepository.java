package az.millers.hcm.budgeting.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.budgeting.domain.BudgetCycle;
import az.millers.hcm.budgeting.domain.BudgetCycleStatus;

public interface BudgetCycleRepository extends JpaRepository<BudgetCycle, UUID> {

    List<BudgetCycle> findByTenantIdOrderByPeriodStartDesc(String tenantId);

    List<BudgetCycle> findByTenantIdAndStatusOrderByPeriodStartDesc(String tenantId, BudgetCycleStatus status);

    Optional<BudgetCycle> findByTenantIdAndCode(String tenantId, String code);
}
