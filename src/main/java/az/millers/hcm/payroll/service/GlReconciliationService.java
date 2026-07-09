package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.payroll.domain.GLAccountType;

/**
 * M467 — GL reconciliation: compare payroll run totals against posted GL journal lines.
 */
@Service
public class GlReconciliationService {

    private final NamedParameterJdbcTemplate jdbc;

    public GlReconciliationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(int year, int month) {
        String tenantId = "default";
        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        // Aggregate payroll totals by component
        String payrollSql = """
            SELECT
              SUM(gross_amount) as gross,
              SUM(net_amount) as net,
              SUM(income_tax) as income_tax,
              SUM(dsmf_employee) as dsmf_employee,
              SUM(dsmf_employer) as dsmf_employer,
              SUM(mmi_employee) as mmi_employee,
              SUM(mmi_employer) as mmi_employer,
              SUM(unempl_employee) as unempl_employee,
              SUM(unempl_employer) as unempl_employer,
              SUM(deduction_amount) as deduction
            FROM payroll.payroll_result pr
            WHERE pr.period_start >= :periodStart
              AND pr.period_start <= :periodEnd
              AND EXISTS (
                SELECT 1 FROM payroll.payroll_run run
                WHERE run.id = pr.run_id
                  AND run.period_year = :year
                  AND run.period_month = :month
                  AND run.tenant_id = :tenantId
              )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("year", year)
                .addValue("month", month)
                .addValue("periodStart", periodStart)
                .addValue("periodEnd", periodEnd);

        Map<String, BigDecimal> payrollTotals = jdbc.queryForObject(payrollSql, params, (rs, rowNum) -> {
            Map<String, BigDecimal> map = new HashMap<>();
            map.put("EARNING", rs.getBigDecimal("gross"));
            map.put("NET_PAY", rs.getBigDecimal("net"));
            map.put("INCOME_TAX", rs.getBigDecimal("income_tax"));
            map.put("DSMF_EMPLOYEE", rs.getBigDecimal("dsmf_employee"));
            map.put("DSMF_EMPLOYER", rs.getBigDecimal("dsmf_employer"));
            map.put("MMI_EMPLOYEE", rs.getBigDecimal("mmi_employee"));
            map.put("MMI_EMPLOYER", rs.getBigDecimal("mmi_employer"));
            map.put("UNEMPLOYMENT_EMPLOYEE", rs.getBigDecimal("unempl_employee"));
            map.put("UNEMPLOYMENT_EMPLOYER", rs.getBigDecimal("unempl_employer"));
            map.put("DEDUCTION", rs.getBigDecimal("deduction"));
            return map;
        });

        if (payrollTotals == null) {
            payrollTotals = new HashMap<>();
        }

        // Aggregate posted GL journal lines by component
        String glSql = """
            SELECT
              gjl.component_kind,
              gjl.account_type,
              SUM(gjl.amount) as total
            FROM payroll.gl_journal_line gjl
            JOIN payroll.gl_journal gj ON gj.id = gjl.journal_id
            JOIN payroll.payroll_run run ON run.id = gj.run_id
            WHERE run.period_year = :year
              AND run.period_month = :month
              AND run.tenant_id = :tenantId
              AND gj.status = 'POSTED'
              AND gj.tenant_id = :tenantId
            GROUP BY gjl.component_kind, gjl.account_type
            """;

        Map<String, BigDecimal> glTotals = new HashMap<>();
        jdbc.query(glSql, params, rs -> {
            String componentKind = rs.getString("component_kind");
            String accountType = rs.getString("account_type");
            BigDecimal total = rs.getBigDecimal("total");
            glTotals.put(componentKind + "_" + accountType, total);
        });

        // Compare and build reconciliation rows
        List<ReconciliationRow> rows = new ArrayList<>();

        // EARNING (DEBIT in GL)
        rows.add(buildRow("Gross Salary", "EARNING", GLAccountType.DEBIT,
                payrollTotals.getOrDefault("EARNING", BigDecimal.ZERO),
                glTotals.getOrDefault("EARNING_DEBIT", BigDecimal.ZERO)));

        // NET_PAY (CREDIT in GL)
        rows.add(buildRow("Net Pay Payable", "NET_PAY", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("NET_PAY", BigDecimal.ZERO),
                glTotals.getOrDefault("NET_PAY_CREDIT", BigDecimal.ZERO)));

        // INCOME_TAX (CREDIT in GL)
        rows.add(buildRow("Income Tax", "INCOME_TAX", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("INCOME_TAX", BigDecimal.ZERO),
                glTotals.getOrDefault("INCOME_TAX_CREDIT", BigDecimal.ZERO)));

        // DSMF employee (CREDIT in GL)
        rows.add(buildRow("DSMF Employee", "DSMF_EMPLOYEE", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("DSMF_EMPLOYEE", BigDecimal.ZERO),
                glTotals.getOrDefault("DSMF_EMPLOYEE_CREDIT", BigDecimal.ZERO)));

        // DSMF employer (CREDIT in GL)
        rows.add(buildRow("DSMF Employer", "DSMF_EMPLOYER", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("DSMF_EMPLOYER", BigDecimal.ZERO),
                glTotals.getOrDefault("DSMF_EMPLOYER_CREDIT", BigDecimal.ZERO)));

        // MMI employee (CREDIT in GL)
        rows.add(buildRow("MMI Employee", "MMI_EMPLOYEE", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("MMI_EMPLOYEE", BigDecimal.ZERO),
                glTotals.getOrDefault("MMI_EMPLOYEE_CREDIT", BigDecimal.ZERO)));

        // MMI employer (CREDIT in GL)
        rows.add(buildRow("MMI Employer", "MMI_EMPLOYER", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("MMI_EMPLOYER", BigDecimal.ZERO),
                glTotals.getOrDefault("MMI_EMPLOYER_CREDIT", BigDecimal.ZERO)));

        // Unemployment employee (CREDIT in GL)
        rows.add(buildRow("Unemployment Employee", "UNEMPLOYMENT_EMPLOYEE", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("UNEMPLOYMENT_EMPLOYEE", BigDecimal.ZERO),
                glTotals.getOrDefault("UNEMPLOYMENT_EMPLOYEE_CREDIT", BigDecimal.ZERO)));

        // Unemployment employer (CREDIT in GL)
        rows.add(buildRow("Unemployment Employer", "UNEMPLOYMENT_EMPLOYER", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("UNEMPLOYMENT_EMPLOYER", BigDecimal.ZERO),
                glTotals.getOrDefault("UNEMPLOYMENT_EMPLOYER_CREDIT", BigDecimal.ZERO)));

        // Other deductions (CREDIT in GL)
        rows.add(buildRow("Other Deductions", "DEDUCTION", GLAccountType.CREDIT,
                payrollTotals.getOrDefault("DEDUCTION", BigDecimal.ZERO),
                glTotals.getOrDefault("DEDUCTION_CREDIT", BigDecimal.ZERO)));

        return new ReconciliationReport(year, month, rows);
    }

    private ReconciliationRow buildRow(String description, String componentKind, GLAccountType accountType,
                                        BigDecimal payrollTotal, BigDecimal glTotal) {
        BigDecimal difference = payrollTotal.subtract(glTotal);
        ReconciliationStatus status = difference.abs().compareTo(new BigDecimal("0.01")) < 0
                ? ReconciliationStatus.MATCHED
                : ReconciliationStatus.DISCREPANCY;

        return new ReconciliationRow(description, componentKind, accountType.name(),
                payrollTotal, glTotal, difference, status);
    }

    public enum ReconciliationStatus {
        MATCHED,
        DISCREPANCY
    }

    public record ReconciliationRow(
            String description,
            String componentKind,
            String accountType,
            BigDecimal payrollTotal,
            BigDecimal glTotal,
            BigDecimal difference,
            ReconciliationStatus status
    ) {}

    public record ReconciliationReport(
            int year,
            int month,
            List<ReconciliationRow> rows
    ) {}
}
