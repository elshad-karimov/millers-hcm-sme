package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.BudgetExceededPolicy;
import az.millers.hcm.compensation.domain.BudgetScopeType;
import az.millers.hcm.compensation.domain.BudgetType;
import az.millers.hcm.compensation.domain.CompConfig;
import az.millers.hcm.compensation.domain.CompensationBudget;
import az.millers.hcm.compensation.repo.CompensationBudgetRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M364 — Compensation budget control: track allocated vs consumed budget by scope.
 */
@Service
public class CompensationBudgetService {

    private static final Logger log = LoggerFactory.getLogger(CompensationBudgetService.class);

    private static final String MODULE = "compensation";
    private static final String ENTITY = "CompensationBudget";

    private final CompensationBudgetRepository repo;
    private final CompConfigService compConfig;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CompensationBudgetService(CompensationBudgetRepository repo,
                                      CompConfigService compConfig,
                                      AuditService audit,
                                      CurrentRequest currentRequest) {
        this.repo = repo;
        this.compConfig = compConfig;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<CompensationBudget> list(BudgetType budgetType, BudgetScopeType scopeType) {
        if (budgetType != null) {
            return repo.findByTenantIdAndBudgetTypeAndIsActiveTrueOrderByCreatedAtDesc(TenantContext.current(), budgetType);
        } else if (scopeType != null) {
            return repo.findByTenantIdAndScopeTypeAndIsActiveTrueOrderByCreatedAtDesc(TenantContext.current(), scopeType);
        } else {
            return repo.findByTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TenantContext.current());
        }
    }

    @Transactional(readOnly = true)
    public CompensationBudget get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compensation budget not found: " + id));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(UUID id) {
        CompensationBudget budget = get(id);
        BigDecimal remaining = remaining(budget);
        return Map.of(
                "amount", budget.getAmount(),
                "consumed", budget.getConsumedAmount(),
                "remaining", remaining
        );
    }

    @Transactional
    public CompensationBudget create(BudgetType budgetType,
                                      BudgetScopeType scopeType,
                                      String scopeRef,
                                      BigDecimal amount,
                                      String currency,
                                      UUID cycleId,
                                      LocalDate effectiveFrom,
                                      LocalDate effectiveTo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("amount must be >= 0");
        }

        if (scopeType != BudgetScopeType.GLOBAL && scopeRef == null) {
            throw new BadRequestException("scope_ref is required for non-GLOBAL scope");
        }

        CompensationBudget budget = new CompensationBudget();
        budget.setTenantId(TenantContext.current());
        budget.setBudgetType(budgetType);
        budget.setScopeType(scopeType);
        budget.setScopeRef(scopeRef);
        budget.setAmount(amount);
        budget.setCurrency(currency != null ? currency : "AZN");
        budget.setCycleId(cycleId);
        budget.setEffectiveFrom(effectiveFrom);
        budget.setEffectiveTo(effectiveTo);

        CompensationBudget saved = repo.save(budget);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, Map.of(
                        "budgetType", budgetType,
                        "scopeType", scopeType,
                        "amount", amount
                ));
        return saved;
    }

    @Transactional
    public CompensationBudget update(UUID id,
                                      BigDecimal amount,
                                      LocalDate effectiveFrom,
                                      LocalDate effectiveTo) {
        CompensationBudget budget = get(id);
        Map<String, Object> before = Map.of("amount", budget.getAmount());

        if (amount != null) {
            budget.setAmount(amount);
        }
        if (effectiveFrom != null) {
            budget.setEffectiveFrom(effectiveFrom);
        }
        if (effectiveTo != null) {
            budget.setEffectiveTo(effectiveTo);
        }

        CompensationBudget saved = repo.save(budget);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", before,
                Map.of("amount", saved.getAmount()));
        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        CompensationBudget budget = get(id);
        budget.setIsActive(false);
        repo.save(budget);
        audit.record(MODULE, ENTITY, id.toString(), "DEACTIVATE", null, null);
    }

    /**
     * Consume a portion of the budget (increment consumed_amount).
     * Called when a salary change is APPLIED.
     */
    @Transactional
    public void consume(UUID budgetId, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Nothing to consume
        }

        CompensationBudget budget = get(budgetId);
        BigDecimal before = budget.getConsumedAmount();
        budget.setConsumedAmount(before.add(delta));

        repo.save(budget);
        audit.record(MODULE, ENTITY, budgetId.toString(), "CONSUME",
                Map.of("before", before), Map.of("after", budget.getConsumedAmount(), "delta", delta));
    }

    /**
     * Check and apply budget policy for a proposed compensation change.
     * Returns { policy, withinBudget, budget, remainingAfter }.
     * Throws BadRequestException if policy is HARD_STOP and budget exceeded.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkAndApply(BudgetScopeType scopeType,
                                              String scopeRef,
                                              BudgetType budgetType,
                                              BigDecimal incrementAmount) {
        if (incrementAmount == null || incrementAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // No budget impact for non-positive increments
            return Map.of("policy", "N/A", "withinBudget", true, "budget", (Object) null, "remainingAfter", BigDecimal.ZERO);
        }

        // Find matching budget (scoped, fallback to GLOBAL)
        CompensationBudget budget;
        if (scopeType != BudgetScopeType.GLOBAL) {
            budget = repo.findActiveBudget(TenantContext.current(), budgetType, scopeType, scopeRef).orElse(null);
        } else {
            budget = null;
        }

        // Fallback to GLOBAL if no scoped budget
        if (budget == null) {
            budget = repo.findActiveGlobalBudget(TenantContext.current(), budgetType).orElse(null);
        }

        // If no budget found, unbudgeted → allow
        if (budget == null) {
            return Map.of("policy", "UNBUDGETED", "withinBudget", true, "budget", (Object) null, "remainingAfter", BigDecimal.ZERO);
        }

        BigDecimal remaining = remaining(budget);
        boolean withinBudget = incrementAmount.compareTo(remaining) <= 0;

        CompConfig config = compConfig.get();
        BudgetExceededPolicy policy = config.getBudgetExceededPolicy();

        if (!withinBudget) {
            // Budget exceeded
            if (policy == BudgetExceededPolicy.HARD_STOP) {
                throw new BadRequestException("BUDGET_EXCEEDED: Remaining budget is "
                        + remaining + " " + budget.getCurrency() + ", but increment is "
                        + incrementAmount + " " + budget.getCurrency());
            }
            // WARNING or EXCEPTION_APPROVAL: log but allow
            log.warn("Budget exceeded: budget {} remaining {}, increment {}. Policy: {}",
                    budget.getId(), remaining, incrementAmount, policy);
        }

        BigDecimal remainingAfter = remaining.subtract(incrementAmount);

        return Map.of(
                "policy", policy.name(),
                "withinBudget", withinBudget,
                "budget", budget,
                "remainingAfter", remainingAfter
        );
    }

    private BigDecimal remaining(CompensationBudget budget) {
        return budget.getAmount().subtract(budget.getConsumedAmount());
    }
}
