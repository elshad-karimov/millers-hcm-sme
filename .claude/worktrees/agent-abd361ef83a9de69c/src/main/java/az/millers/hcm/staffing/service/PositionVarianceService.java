package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.staffing.api.dto.PositionVarianceDtos.PositionVarianceRow;
import az.millers.hcm.staffing.api.dto.PositionVarianceDtos.VarianceReport;
import az.millers.hcm.staffing.api.dto.PositionVarianceDtos.VarianceTotals;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionBudget;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.repo.PositionBudgetRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M258 — Position budget-vs-actual variance dashboard (PRD §19).
 *
 * <p>For a given {@link YearMonth}, joins three sources:
 * <ul>
 *   <li>{@code staffing.position_budget} — the planned monthly fully-loaded
 *       cost (basic + allowances + employer tax + bonus + overtime +
 *       benefits) from M244;
 *   <li>{@code payroll.payroll_result} — the actual fully-loaded payroll
 *       cost for the month, aggregated by employee;
 *   <li>{@code core_hr.employee.position_id} — the bridge attributing
 *       each payroll row to a position.
 * </ul>
 *
 * <p>For each position with either a budget set OR active payroll cost in
 * the month, returns one row with the planned, actual, variance amount
 * and variance percent. Sorted by absolute variance descending so the
 * biggest miss-by-amount sits at the top.
 *
 * <p>Note: position_id is captured from the employee's CURRENT
 * position, not their position at the time of the payroll run. For
 * end-of-month payroll this is normally identical; for mid-month
 * transfers it is a known approximation. A future enhancement could
 * snapshot position_id onto payroll_result at run time — that's a
 * separate ETL milestone.
 */
@Service
public class PositionVarianceService {

    private final PositionRepository positions;
    private final PositionBudgetRepository budgets;
    private final PayrollResultRepository payrollResults;
    private final EmployeeRepository employees;

    public PositionVarianceService(PositionRepository positions,
                                    PositionBudgetRepository budgets,
                                    PayrollResultRepository payrollResults,
                                    EmployeeRepository employees) {
        this.positions = positions;
        this.budgets = budgets;
        this.payrollResults = payrollResults;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public VarianceReport compute(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate periodStart = ym.atDay(1);

        // 1. Aggregate actual fully-loaded payroll cost per employee.
        // Fully-loaded = gross + employer-side statutory contributions
        // (DSMF / MMI / unemployment employer portions). This mirrors
        // what the M244 PositionBudget.budgeted_employer_tax line is
        // meant to cover, so the comparison is apples-to-apples.
        List<PayrollResult> results = payrollResults.findByPeriodStart(periodStart);
        Map<UUID, BigDecimal> actualPerEmployee = new HashMap<>();
        for (PayrollResult r : results) {
            BigDecimal loaded = r.getGrossAmount()
                    .add(nz(r.getDsmfEmployer()))
                    .add(nz(r.getMmiEmployer()))
                    .add(nz(r.getUnemplEmployer()));
            actualPerEmployee.merge(r.getEmployeeId(), loaded, BigDecimal::add);
        }

        // 2. Roll up actuals by position via employee.position_id.
        Map<UUID, BigDecimal> actualPerPosition = new HashMap<>();
        Map<UUID, Integer> headcountPerPosition = new HashMap<>();
        for (Map.Entry<UUID, BigDecimal> e : actualPerEmployee.entrySet()) {
            Optional<Employee> emp = employees.findById(e.getKey());
            if (emp.isEmpty() || emp.get().getPositionId() == null) continue;
            UUID positionId = emp.get().getPositionId();
            actualPerPosition.merge(positionId, e.getValue(), BigDecimal::add);
            headcountPerPosition.merge(positionId, 1, Integer::sum);
        }

        // 3. Walk every ACTIVE position. Show even budget-set positions
        // with zero actual (e.g. a frozen vacancy still consuming the
        // budget line) and every position with actual cost even if no
        // budget exists yet (over-spend warning).
        List<Position> all = positions.findByStatus(PositionStatus.ACTIVE);

        List<PositionVarianceRow> rows = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        for (Position p : all) {
            BigDecimal actual = actualPerPosition.getOrDefault(p.getId(), BigDecimal.ZERO);
            int occupied = headcountPerPosition.getOrDefault(p.getId(), 0);

            Optional<PositionBudget> b = budgets.currentBudget(p.getId(), periodStart);
            BigDecimal budgeted = b.map(this::sumBudgeted).orElse(null);

            // Skip the truly silent positions — no budget set, no
            // payroll cost. Nothing to say.
            if ((budgeted == null || budgeted.signum() == 0) && actual.signum() == 0) {
                continue;
            }

            BigDecimal variance = actual.subtract(budgeted == null ? BigDecimal.ZERO : budgeted);
            BigDecimal variancePct = null;
            if (budgeted != null && budgeted.signum() > 0) {
                variancePct = variance
                        .multiply(BigDecimal.valueOf(100))
                        .divide(budgeted, 1, java.math.RoundingMode.HALF_UP);
            }

            // Classify so the SPA can colour-code: OVER (red), UNDER
            // (blue), ON_TRACK (green), NO_BUDGET (yellow / warning).
            String status;
            if (budgeted == null) {
                status = "NO_BUDGET";
            } else if (variancePct != null && variancePct.abs().compareTo(BigDecimal.valueOf(5)) <= 0) {
                status = "ON_TRACK";
            } else if (variance.signum() > 0) {
                status = "OVER";
            } else {
                status = "UNDER";
            }

            rows.add(new PositionVarianceRow(
                    p.getId(), p.getCode(), p.getTitle(),
                    p.getOrgUnitLabel(),
                    p.getApprovedHeadcount(), occupied,
                    budgeted, actual, variance, variancePct,
                    p.getCurrency(), status));
            if (budgeted != null) totalBudget = totalBudget.add(budgeted);
            totalActual = totalActual.add(actual);
        }

        // Biggest-impact rows first — abs(variance) descending.
        rows.sort(Comparator
                .comparing(
                        (PositionVarianceRow r) -> r.variance() == null ? BigDecimal.ZERO : r.variance().abs())
                .reversed());

        VarianceTotals totals = new VarianceTotals(
                totalBudget, totalActual,
                totalActual.subtract(totalBudget),
                (int) rows.stream().filter(r -> "OVER".equals(r.status())).count(),
                (int) rows.stream().filter(r -> "UNDER".equals(r.status())).count(),
                (int) rows.stream().filter(r -> "NO_BUDGET".equals(r.status())).count());

        return new VarianceReport(year, month, rows.size(), totals, rows);
    }

    private BigDecimal sumBudgeted(PositionBudget b) {
        return nz(b.getBudgetedBasicSalary())
                .add(nz(b.getBudgetedAllowances()))
                .add(nz(b.getBudgetedEmployerTax()))
                .add(nz(b.getBudgetedBonus()))
                .add(nz(b.getBudgetedOvertime()))
                .add(nz(b.getBudgetedBenefits()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
