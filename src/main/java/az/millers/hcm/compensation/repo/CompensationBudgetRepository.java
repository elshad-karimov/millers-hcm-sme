package az.millers.hcm.compensation.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.compensation.domain.BudgetScopeType;
import az.millers.hcm.compensation.domain.BudgetType;
import az.millers.hcm.compensation.domain.CompensationBudget;

public interface CompensationBudgetRepository extends JpaRepository<CompensationBudget, UUID> {
    List<CompensationBudget> findByTenantIdAndIsActiveTrueOrderByCreatedAtDesc(String tenantId);
    List<CompensationBudget> findByTenantIdAndBudgetTypeAndIsActiveTrueOrderByCreatedAtDesc(
            String tenantId, BudgetType budgetType);
    List<CompensationBudget> findByTenantIdAndScopeTypeAndIsActiveTrueOrderByCreatedAtDesc(
            String tenantId, BudgetScopeType scopeType);

    @Query("SELECT b FROM CompensationBudget b WHERE b.tenantId = ?1 AND b.budgetType = ?2 " +
           "AND b.scopeType = ?3 AND b.scopeRef = ?4 AND b.isActive = true")
    Optional<CompensationBudget> findActiveBudget(String tenantId, BudgetType budgetType,
                                                    BudgetScopeType scopeType, String scopeRef);

    @Query("SELECT b FROM CompensationBudget b WHERE b.tenantId = ?1 AND b.budgetType = ?2 " +
           "AND b.scopeType = 'GLOBAL' AND b.isActive = true")
    Optional<CompensationBudget> findActiveGlobalBudget(String tenantId, BudgetType budgetType);

    boolean existsByScopeTypeAndScopeRef(BudgetScopeType scopeType, String scopeRef);
}
