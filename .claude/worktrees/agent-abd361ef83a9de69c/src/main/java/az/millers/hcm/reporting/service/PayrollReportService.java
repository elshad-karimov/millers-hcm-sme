package az.millers.hcm.reporting.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.BonusReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.BonusReportRow;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.DeductionReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.DeductionReportRow;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.FinalSettlementReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.FinalSettlementRow;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.OvertimeReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.OvertimeReportRow;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.PayrollVarianceReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.SocialInsuranceReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.SocialInsuranceRow;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.TaxReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.TaxReportRow;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.VariancePeriod;

/**
 * Payroll-specific report computations (M225 / PRD §8.9.9).
 *
 * <p>Seven reports are implemented here — all are read-only aggregate
 * queries executed via {@link NamedParameterJdbcTemplate}, following the
 * same pattern as {@link ReportService}. Each run-scoped report resolves
 * the run's period and currency from the {@code payroll_run} header row
 * before querying the detail tables.
 */
@Service
public class PayrollReportService {

    private final NamedParameterJdbcTemplate jdbc;

    public PayrollReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private record RunMeta(UUID id, String runNo, int year, int month, String currency) {}

    private RunMeta fetchRun(UUID runId) {
        String sql = "SELECT id, run_no, period_year, period_month, currency"
                + " FROM payroll.payroll_run WHERE id = :id";
        var rows = jdbc.query(sql, new MapSqlParameterSource("id", runId),
                (rs, i) -> new RunMeta(
                        (UUID) rs.getObject("id"),
                        rs.getString("run_no"),
                        rs.getInt("period_year"),
                        rs.getInt("period_month"),
                        rs.getString("currency")));
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Payroll run not found: " + runId);
        }
        return rows.get(0);
    }

    private static BigDecimal bd(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v == null ? BigDecimal.ZERO : v;
    }

    // ── 1. Tax Report ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TaxReport taxReport(UUID runId) {
        RunMeta run = fetchRun(runId);

        String sql = """
                SELECT r.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       r.gross_amount,
                       r.income_tax
                FROM payroll.payroll_result r
                JOIN core_hr.employee e ON e.id = r.employee_id
                WHERE r.run_id = :runId
                ORDER BY e.employee_no
                """;

        List<TaxReportRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("runId", runId),
                (rs, i) -> {
                    BigDecimal gross = bd(rs, "gross_amount");
                    BigDecimal tax   = bd(rs, "income_tax");
                    BigDecimal rate  = gross.signum() > 0
                            ? tax.multiply(BigDecimal.valueOf(100))
                                  .divide(gross, 2, RoundingMode.HALF_UP)
                            : null;
                    return new TaxReportRow(
                            (UUID) rs.getObject("employee_id"),
                            rs.getString("employee_no"),
                            rs.getString("full_name"),
                            gross, tax, rate);
                });

        BigDecimal totalGross = rows.stream().map(TaxReportRow::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax   = rows.stream().map(TaxReportRow::incomeTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TaxReport(run.id(), run.runNo(), run.year(), run.month(),
                run.currency(), totalGross, totalTax, rows);
    }

    // ── 2. Social-Insurance Report ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public SocialInsuranceReport socialInsuranceReport(UUID runId) {
        RunMeta run = fetchRun(runId);

        String sql = """
                SELECT r.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       r.gross_amount,
                       r.dsmf_employee,   r.dsmf_employer,
                       r.mmi_employee,    r.mmi_employer,
                       r.unempl_employee, r.unempl_employer
                FROM payroll.payroll_result r
                JOIN core_hr.employee e ON e.id = r.employee_id
                WHERE r.run_id = :runId
                ORDER BY e.employee_no
                """;

        List<SocialInsuranceRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("runId", runId),
                (rs, i) -> {
                    BigDecimal dsmfEmp   = bd(rs, "dsmf_employee");
                    BigDecimal dsmfEr    = bd(rs, "dsmf_employer");
                    BigDecimal mmiEmp    = bd(rs, "mmi_employee");
                    BigDecimal mmiEr     = bd(rs, "mmi_employer");
                    BigDecimal unemplEmp = bd(rs, "unempl_employee");
                    BigDecimal unemplEr  = bd(rs, "unempl_employer");
                    return new SocialInsuranceRow(
                            (UUID) rs.getObject("employee_id"),
                            rs.getString("employee_no"),
                            rs.getString("full_name"),
                            bd(rs, "gross_amount"),
                            dsmfEmp, dsmfEr, mmiEmp, mmiEr, unemplEmp, unemplEr,
                            dsmfEmp.add(mmiEmp).add(unemplEmp),
                            dsmfEr.add(mmiEr).add(unemplEr));
                });

        BigDecimal totDsmfEmp   = sum(rows, r -> r.dsmfEmployee());
        BigDecimal totDsmfEr    = sum(rows, r -> r.dsmfEmployer());
        BigDecimal totMmiEmp    = sum(rows, r -> r.mmiEmployee());
        BigDecimal totMmiEr     = sum(rows, r -> r.mmiEmployer());
        BigDecimal totUnemplEmp = sum(rows, r -> r.unemplEmployee());
        BigDecimal totUnemplEr  = sum(rows, r -> r.unemplEmployer());

        return new SocialInsuranceReport(run.id(), run.runNo(), run.year(), run.month(),
                run.currency(), totDsmfEmp, totDsmfEr, totMmiEmp, totMmiEr,
                totUnemplEmp, totUnemplEr, rows);
    }

    // ── 3. Deduction Report ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DeductionReport deductionReport(UUID runId) {
        RunMeta run = fetchRun(runId);
        int periodSeq = run.year() * 12 + run.month();

        // Active/completed deductions whose date window covers this period,
        // restricted to employees who appear in the run.
        String sql = """
                SELECT e.id AS employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       d.deduction_type,
                       d.description,
                       d.amount_per_period,
                       d.recovered_amount,
                       d.total_amount
                FROM payroll.payroll_deduction d
                JOIN core_hr.employee e ON e.id = d.employee_id
                WHERE d.status != 'CANCELLED'
                  AND (d.start_period_year * 12 + d.start_period_month) <= :period
                  AND (d.end_period_year IS NULL
                       OR (d.end_period_year * 12 + d.end_period_month) >= :period)
                  AND EXISTS (
                      SELECT 1 FROM payroll.payroll_result r
                      WHERE r.run_id = :runId AND r.employee_id = d.employee_id
                  )
                ORDER BY e.employee_no, d.deduction_type
                """;

        List<DeductionReportRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("runId", runId).addValue("period", periodSeq),
                (rs, i) -> new DeductionReportRow(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("deduction_type"),
                        rs.getString("description"),
                        bd(rs, "amount_per_period"),
                        rs.getBigDecimal("recovered_amount"),
                        rs.getBigDecimal("total_amount")));

        BigDecimal total = rows.stream().map(DeductionReportRow::amountPerPeriod)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Cross-check total against the payroll_result aggregate for the run.
        BigDecimal resultTotal = jdbc.queryForObject(
                "SELECT COALESCE(SUM(deduction_amount), 0)"
                        + " FROM payroll.payroll_result WHERE run_id = :runId",
                new MapSqlParameterSource("runId", runId),
                BigDecimal.class);

        // Prefer the result-table aggregate (it is what was actually applied).
        BigDecimal reportedTotal = resultTotal != null ? resultTotal : total;

        return new DeductionReport(run.id(), run.runNo(), run.year(), run.month(),
                run.currency(), reportedTotal, rows);
    }

    // ── 4. Bonus Report ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BonusReport bonusReport(UUID runId) {
        RunMeta run = fetchRun(runId);

        String sql = """
                SELECT b.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       b.bonus_type,
                       b.amount,
                       b.note
                FROM payroll.payroll_bonus b
                JOIN core_hr.employee e ON e.id = b.employee_id
                WHERE b.run_id = :runId
                ORDER BY e.employee_no, b.bonus_type
                """;

        List<BonusReportRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("runId", runId),
                (rs, i) -> new BonusReportRow(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getString("bonus_type"),
                        bd(rs, "amount"),
                        rs.getString("note")));

        BigDecimal total = rows.stream().map(BonusReportRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BonusReport(run.id(), run.runNo(), run.year(), run.month(),
                run.currency(), total, rows);
    }

    // ── 5. Overtime Report ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OvertimeReport overtimeReport(UUID runId) {
        RunMeta run = fetchRun(runId);

        String sql = """
                SELECT r.employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       r.overtime_hours,
                       r.overtime_pay,
                       r.gross_amount
                FROM payroll.payroll_result r
                JOIN core_hr.employee e ON e.id = r.employee_id
                WHERE r.run_id = :runId
                  AND r.overtime_hours > 0
                ORDER BY r.overtime_pay DESC, e.employee_no
                """;

        List<OvertimeReportRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("runId", runId),
                (rs, i) -> new OvertimeReportRow(
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        bd(rs, "overtime_hours"),
                        bd(rs, "overtime_pay"),
                        bd(rs, "gross_amount")));

        BigDecimal totalHours = rows.stream().map(OvertimeReportRow::overtimeHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPay   = rows.stream().map(OvertimeReportRow::overtimePay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OvertimeReport(run.id(), run.runNo(), run.year(), run.month(),
                run.currency(), totalHours, totalPay, rows);
    }

    // ── 6. Final-Settlement Report ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public FinalSettlementReport finalSettlementReport(int year) {
        String sql = """
                SELECT t.id              AS termination_id,
                       t.termination_no,
                       e.id             AS employee_id,
                       e.employee_no,
                       e.first_name || ' ' || e.last_name AS full_name,
                       t.effective_date,
                       COALESCE(t.unused_leave_payout,    0) AS unused_leave_payout,
                       COALESCE(t.severance_amount,       0) AS severance_amount,
                       COALESCE(t.final_settlement_amount,0) AS total_settlement,
                       COALESCE(t.currency, 'AZN')           AS currency
                FROM payroll.payroll_run pr
                JOIN lifecycle.termination_request t ON t.id = pr.termination_id
                JOIN core_hr.employee e ON e.id = t.employee_id
                WHERE pr.run_type = 'FINAL_SETTLEMENT'
                  AND pr.period_year = :year
                  AND pr.status IN ('APPROVED','PAID','CLOSED')
                ORDER BY t.effective_date DESC, e.employee_no
                """;

        List<FinalSettlementRow> rows = jdbc.query(sql,
                new MapSqlParameterSource("year", year),
                (rs, i) -> new FinalSettlementRow(
                        (UUID) rs.getObject("termination_id"),
                        rs.getString("termination_no"),
                        (UUID) rs.getObject("employee_id"),
                        rs.getString("employee_no"),
                        rs.getString("full_name"),
                        rs.getDate("effective_date").toLocalDate(),
                        bd(rs, "unused_leave_payout"),
                        bd(rs, "severance_amount"),
                        bd(rs, "total_settlement"),
                        rs.getString("currency")));

        BigDecimal total = rows.stream().map(FinalSettlementRow::totalSettlement)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinalSettlementReport(year, total, rows.size(), rows);
    }

    // ── 7. Payroll-Variance Report ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public PayrollVarianceReport varianceReport(int year) {
        String sql = """
                SELECT run_no, period_year, period_month, status,
                       employee_count, currency,
                       total_gross, total_net, total_income_tax
                FROM payroll.payroll_run
                WHERE period_year = :year
                  AND run_type = 'REGULAR'
                  AND status IN ('APPROVED','PAID','CLOSED')
                ORDER BY period_month
                """;

        record RunRow(String runNo, int year, int month, String status,
                      int headcount, String currency,
                      BigDecimal gross, BigDecimal net, BigDecimal tax) {}

        List<RunRow> raw = jdbc.query(sql,
                new MapSqlParameterSource("year", year),
                (rs, i) -> new RunRow(
                        rs.getString("run_no"),
                        rs.getInt("period_year"),
                        rs.getInt("period_month"),
                        rs.getString("status"),
                        rs.getInt("employee_count"),
                        rs.getString("currency"),
                        bd(rs, "total_gross"),
                        bd(rs, "total_net"),
                        bd(rs, "total_income_tax")));

        String currency = raw.isEmpty() ? "AZN" : raw.get(0).currency();

        List<VariancePeriod> periods = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            RunRow r = raw.get(i);
            BigDecimal variance    = null;
            BigDecimal variancePct = null;
            if (i > 0) {
                BigDecimal prev = raw.get(i - 1).gross();
                variance = r.gross().subtract(prev);
                variancePct = prev.signum() != 0
                        ? variance.multiply(BigDecimal.valueOf(100))
                                   .divide(prev, 2, RoundingMode.HALF_UP)
                        : null;
            }
            periods.add(new VariancePeriod(r.year(), r.month(), r.runNo(), r.status(),
                    r.headcount(), r.gross(), r.net(), r.tax(), variance, variancePct));
        }

        return new PayrollVarianceReport(year, currency, periods);
    }

    // ── Utility ────────────────────────────────────────────────────────────

    private static <T> BigDecimal sum(List<T> list,
                                      java.util.function.Function<T, BigDecimal> extractor) {
        return list.stream()
                .map(extractor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
