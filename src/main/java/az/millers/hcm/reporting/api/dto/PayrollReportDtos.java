package az.millers.hcm.reporting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the seven payroll-specific reports mandated by PRD §8.9.9
 * (M225).
 */
public final class PayrollReportDtos {

    private PayrollReportDtos() {}

    // ── Tax Report ─────────────────────────────────────────────────────────

    public record TaxReportRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal grossAmount,
            BigDecimal incomeTax,
            /** Income-tax / gross × 100; null when gross is zero. */
            BigDecimal effectiveTaxRatePct) {}

    public record TaxReport(
            UUID runId,
            String runNo,
            int periodYear,
            int periodMonth,
            String currency,
            BigDecimal totalGross,
            BigDecimal totalIncomeTax,
            List<TaxReportRow> rows) {}

    // ── Social-Insurance (DSMF / MMI / Unemployment) Report ───────────────

    public record SocialInsuranceRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal grossAmount,
            BigDecimal dsmfEmployee,
            BigDecimal dsmfEmployer,
            BigDecimal mmiEmployee,
            BigDecimal mmiEmployer,
            BigDecimal unemplEmployee,
            BigDecimal unemplEmployer,
            BigDecimal totalEmployeeContribution,
            BigDecimal totalEmployerContribution) {}

    public record SocialInsuranceReport(
            UUID runId,
            String runNo,
            int periodYear,
            int periodMonth,
            String currency,
            BigDecimal totalDsmfEmployee,
            BigDecimal totalDsmfEmployer,
            BigDecimal totalMmiEmployee,
            BigDecimal totalMmiEmployer,
            BigDecimal totalUnemplEmployee,
            BigDecimal totalUnemplEmployer,
            List<SocialInsuranceRow> rows) {}

    // ── Deduction Report ───────────────────────────────────────────────────

    public record DeductionReportRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            String deductionType,
            String description,
            BigDecimal amountPerPeriod,
            /** Cumulative recovered so far (ADVANCE_RECOVERY only; null otherwise). */
            BigDecimal recoveredAmount,
            BigDecimal totalAmount) {}

    public record DeductionReport(
            UUID runId,
            String runNo,
            int periodYear,
            int periodMonth,
            String currency,
            BigDecimal totalDeductions,
            List<DeductionReportRow> rows) {}

    // ── Bonus Report ───────────────────────────────────────────────────────

    public record BonusReportRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            String bonusType,
            BigDecimal amount,
            String note) {}

    public record BonusReport(
            UUID runId,
            String runNo,
            int periodYear,
            int periodMonth,
            String currency,
            BigDecimal totalBonus,
            List<BonusReportRow> rows) {}

    // ── Overtime Report ────────────────────────────────────────────────────

    public record OvertimeReportRow(
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal overtimeHours,
            BigDecimal overtimePay,
            BigDecimal grossAmount) {}

    public record OvertimeReport(
            UUID runId,
            String runNo,
            int periodYear,
            int periodMonth,
            String currency,
            BigDecimal totalOvertimeHours,
            BigDecimal totalOvertimePay,
            List<OvertimeReportRow> rows) {}

    // ── Final-Settlement Report ────────────────────────────────────────────

    public record FinalSettlementRow(
            UUID terminationId,
            String terminationNo,
            UUID employeeId,
            String employeeNo,
            String fullName,
            LocalDate effectiveDate,
            BigDecimal unusedLeavePayout,
            BigDecimal severanceAmount,
            BigDecimal totalSettlement,
            String currency) {}

    public record FinalSettlementReport(
            int year,
            BigDecimal totalSettlement,
            int count,
            List<FinalSettlementRow> rows) {}

    // ── Payroll-Variance Report ────────────────────────────────────────────

    public record VariancePeriod(
            int periodYear,
            int periodMonth,
            String runNo,
            String status,
            int headcount,
            BigDecimal totalGross,
            BigDecimal totalNet,
            BigDecimal totalIncomeTax,
            /** Gross change vs preceding period; null for the first period in the series. */
            BigDecimal grossVariance,
            /** Variance as a percentage; null when no preceding period or preceding gross is zero. */
            BigDecimal grossVariancePct) {}

    public record PayrollVarianceReport(
            int year,
            String currency,
            List<VariancePeriod> periods) {}
}
