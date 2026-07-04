package az.millers.hcm.budgeting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.budgeting.domain.BudgetControlRule;
import az.millers.hcm.budgeting.domain.ControlAction;
import az.millers.hcm.budgeting.domain.TriggerPoint;
import az.millers.hcm.budgeting.repo.BudgetControlRuleRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_20 M428 — Budget control service (PRD §20.6).
 * check(triggerPoint, orgUnitId, additionalAnnualAmount) looks up the active rule
 * for the trigger, finds the current OPEN/APPROVED cycle's department_budget for
 * that org-unit, computes (consumed + additional) / budget, returns OK/WARN/BLOCK.
 */
@Service
public class BudgetControlService {

    private static final Logger log = LoggerFactory.getLogger(BudgetControlService.class);
    private static final String TENANT = "default";
    private static final String MODULE = "BUDGETING";

    private final BudgetControlRuleRepository repo;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BudgetControlService(BudgetControlRuleRepository repo,
                                NamedParameterJdbcTemplate jdbc,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<BudgetControlRule> listRules() {
        return repo.findByTenantIdOrderByTriggerPoint(TENANT);
    }

    @Transactional
    public BudgetControlRule upsertRule(TriggerPoint triggerPoint, ControlAction action, BigDecimal thresholdPct) {
        BudgetControlRule rule = repo.findByTenantIdAndTriggerPointAndActiveTrue(TENANT, triggerPoint)
                .orElseGet(() -> {
                    BudgetControlRule r = new BudgetControlRule();
                    r.setTenantId(TENANT);
                    r.setTriggerPoint(triggerPoint);
                    r.setCreatedBy(currentRequest.username());
                    return r;
                });
        rule.setAction(action);
        rule.setThresholdPct(thresholdPct);
        BudgetControlRule saved = repo.save(rule);
        audit.record(MODULE, "BudgetControlRule", saved.getId().toString(), "UPSERT", null, null);
        return saved;
    }

    /**
     * Check budget control for a proposed transaction.
     * @param triggerPoint    trigger (SALARY_CHANGE, NEW_HIRE, etc.)
     * @param orgUnitId       department UUID
     * @param additionalAnnualAmount  additional annual cost (prorated to monthly if needed)
     * @return result map: { result: OK | WARN | BLOCK, message: String }
     */
    @Transactional(readOnly = true)
    public Map<String, Object> check(TriggerPoint triggerPoint, UUID orgUnitId, BigDecimal additionalAnnualAmount) {
        // 1. Find active rule for this trigger
        Optional<BudgetControlRule> ruleOpt = repo.findByTenantIdAndTriggerPointAndActiveTrue(TENANT, triggerPoint);
        if (ruleOpt.isEmpty()) {
            return Map.of("result", "OK", "message", "No budget control rule defined for " + triggerPoint);
        }
        BudgetControlRule rule = ruleOpt.get();

        // 2. Find current OPEN or APPROVED budget cycle's department budget for this org-unit
        String sql = """
            SELECT db.id, db.salary_budget, db.consumed_amount
            FROM budgeting.department_budget db
            JOIN budgeting.budget_cycle bc ON bc.id = db.cycle_id
            WHERE db.tenant_id = :tenant
              AND db.org_unit_id = :orgUnit
              AND bc.status IN ('OPEN', 'LOCKED')
              AND bc.period_start <= :today
              AND bc.period_end >= :today
              AND db.status IN ('APPROVED', 'SUBMITTED')
            ORDER BY bc.period_end DESC
            LIMIT 1
        """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql,
                Map.of("tenant", TENANT, "orgUnit", orgUnitId, "today", LocalDate.now()));

        if (rows.isEmpty()) {
            return Map.of("result", "OK", "message", "No active budget found for department " + orgUnitId);
        }

        Map<String, Object> budget = rows.get(0);
        BigDecimal budgetAmount = (BigDecimal) budget.get("salary_budget");
        BigDecimal consumed = (BigDecimal) budget.get("consumed_amount");

        // 3. Compute new utilization %
        BigDecimal monthlyAdditional = additionalAnnualAmount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal newConsumed = consumed.add(monthlyAdditional);
        BigDecimal utilizationPct = budgetAmount.compareTo(BigDecimal.ZERO) > 0
                ? newConsumed.divide(budgetAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // 4. Apply rule
        if (utilizationPct.compareTo(rule.getThresholdPct()) > 0) {
            String message = String.format(
                    "Budget utilization %.2f%% exceeds threshold %.2f%% for %s (budget: %s, consumed: %s, additional: %s)",
                    utilizationPct, rule.getThresholdPct(), triggerPoint,
                    budgetAmount, consumed, monthlyAdditional
            );
            audit.record(MODULE, "BudgetControl", orgUnitId.toString(),
                    "CHECK_" + rule.getAction(), triggerPoint.name(), message);
            return Map.of("result", rule.getAction().name(), "message", message);
        }

        return Map.of("result", "OK", "message", "Within budget");
    }
}
