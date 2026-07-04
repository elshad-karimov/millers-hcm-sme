package az.millers.hcm.budgeting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.budgeting.api.dto.DepartmentVariance;
import az.millers.hcm.budgeting.api.dto.VarianceStatus;
import az.millers.hcm.budgeting.domain.BudgetCycle;
import az.millers.hcm.budgeting.repo.BudgetCycleRepository;
import az.millers.hcm.budgeting.repo.DepartmentBudgetRepository;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_20 M427 — Budget variance: actual payroll cost vs department budget (PRD §20.5).
 * Aggregates payroll_result gross within the cycle period, grouped by employee.org_unit_id.
 * HR/Finance get full view; DEPARTMENT_MANAGER sees ONLY their own org-unit variance aggregate.
 */
@Service
public class BudgetVarianceService {

    private static final String TENANT = "default";

    private final BudgetCycleRepository cycles;
    private final DepartmentBudgetRepository budgets;
    private final EmployeeRepository employees;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;

    public BudgetVarianceService(BudgetCycleRepository cycles,
                                 DepartmentBudgetRepository budgets,
                                 EmployeeRepository employees,
                                 NamedParameterJdbcTemplate jdbc,
                                 CurrentRequest currentRequest,
                                 AccessScopeService accessScope) {
        this.cycles = cycles;
        this.budgets = budgets;
        this.employees = employees;
        this.jdbc = jdbc;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
    }

    /**
     * Compute budget-vs-actual variance for a cycle.
     * Returns per-department rows. If user is DEPARTMENT_MANAGER, filter to ONLY their org-unit.
     */
    @Transactional(readOnly = true)
    public List<DepartmentVariance> variance(UUID cycleId) {
        BudgetCycle cycle = cycles.findById(cycleId)
                .filter(c -> TENANT.equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Budget cycle not found: " + cycleId));

        LocalDate start = cycle.getPeriodStart();
        LocalDate end = cycle.getPeriodEnd();

        // Fetch actual payroll cost per org-unit within the cycle period
        // payroll_result.gross_pay summed where payroll_run.period_start/end overlaps cycle period
        String actualSql = """
            SELECT e.org_unit_id, COALESCE(SUM(pr.gross_pay), 0) AS actual_cost
            FROM payroll.payroll_result pr
            JOIN payroll.payroll_run prun ON prun.id = pr.run_id
            JOIN core_hr.employee e ON e.id = pr.employee_id
            WHERE e.tenant_id = :tenant
              AND prun.period_end >= :start
              AND prun.period_start <= :end
              AND e.org_unit_id IS NOT NULL
            GROUP BY e.org_unit_id
        """;
        List<Map<String, Object>> actuals = jdbc.queryForList(actualSql,
                Map.of("tenant", TENANT, "start", start, "end", end));

        Map<UUID, BigDecimal> actualPerOrgUnit = actuals.stream()
                .collect(java.util.stream.Collectors.toMap(
                        m -> (UUID) m.get("org_unit_id"),
                        m -> (BigDecimal) m.get("actual_cost")
                ));

        // Fetch department budgets for this cycle
        List<az.millers.hcm.budgeting.domain.DepartmentBudget> deptBudgets = budgets.findByCycleIdOrderByOrgUnitId(cycleId);

        // Check if user is DEPARTMENT_MANAGER → filter to own org-unit only
        UUID managerOrgUnit = null;
        if (currentRequest.hasRole("DEPARTMENT_MANAGER") && !currentRequest.hasRole("HR_ADMIN")) {
            Employee caller = employees.findByUsername(currentRequest.username()).orElse(null);
            if (caller != null) {
                managerOrgUnit = caller.getOrgUnitId();
            }
        }

        List<DepartmentVariance> results = new ArrayList<>();
        for (az.millers.hcm.budgeting.domain.DepartmentBudget db : deptBudgets) {
            // If manager, skip other departments
            if (managerOrgUnit != null && !managerOrgUnit.equals(db.getOrgUnitId())) {
                continue;
            }

            BigDecimal budgetAmount = db.getSalaryBudget(); // simplified: use salary budget only
            BigDecimal actualCost = actualPerOrgUnit.getOrDefault(db.getOrgUnitId(), BigDecimal.ZERO);
            BigDecimal variance = budgetAmount.subtract(actualCost);
            BigDecimal pct = budgetAmount.compareTo(BigDecimal.ZERO) > 0
                    ? actualCost.divide(budgetAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            VarianceStatus status;
            if (pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                status = VarianceStatus.OVER;
            } else if (pct.compareTo(BigDecimal.valueOf(90)) >= 0) {
                status = VarianceStatus.WARNING;
            } else {
                status = VarianceStatus.UNDER;
            }

            results.add(new DepartmentVariance(
                    db.getOrgUnitId(),
                    budgetAmount,
                    actualCost,
                    variance,
                    pct.setScale(2, RoundingMode.HALF_UP),
                    status
            ));
        }

        return results;
    }
}
