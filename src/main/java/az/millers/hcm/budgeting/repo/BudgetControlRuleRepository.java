package az.millers.hcm.budgeting.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.budgeting.domain.BudgetControlRule;
import az.millers.hcm.budgeting.domain.TriggerPoint;

public interface BudgetControlRuleRepository extends JpaRepository<BudgetControlRule, UUID> {

    List<BudgetControlRule> findByTenantIdOrderByTriggerPoint(String tenantId);

    Optional<BudgetControlRule> findByTenantIdAndTriggerPointAndActiveTrue(String tenantId, TriggerPoint triggerPoint);
}
