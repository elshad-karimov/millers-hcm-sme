package az.millers.hcm.payroll.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.BankAccount;
import az.millers.hcm.payroll.domain.BankFileFormat;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.repo.BankAccountRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;

/**
 * Generates payroll bank-file exports (PRD §8.9.6 / §17.3 / M163).
 *
 * <p>Supports four Azerbaijani bank templates (ABB, Kapital Bank, PASHA Bank,
 * Bank Respublika) plus a generic CSV fallback.  Each template uses the exact
 * column layout required by the bank's corporate salary-disbursement portal.
 */
@Service
public class BankFileService {

    private final PayrollRunRepository runs;
    private final PayrollResultRepository results;
    private final EmployeeRepository employees;
    private final BankAccountRepository bankAccounts;

    public BankFileService(PayrollRunRepository runs,
                            PayrollResultRepository results,
                            EmployeeRepository employees,
                            BankAccountRepository bankAccounts) {
        this.runs = runs;
        this.results = results;
        this.employees = employees;
        this.bankAccounts = bankAccounts;
    }

    // ── Public format-aware entry point (M163) ──────────────────────────────

    /**
     * Renders a bank-payment file for the given run in the requested format.
     *
     * @param runId  payroll run UUID
     * @param format output format (ABB, KAPITAL, PASHA, RESPUBLIKA, or CSV)
     * @return file content as a string (CSV, TSV, or XML depending on format)
     */
    @Transactional(readOnly = true)
    public String renderBankFile(UUID runId, BankFileFormat format) {
        PayrollRun run = requireApprovedRun(runId);
        java.util.List<RowData> rows = buildRows(run);
        return switch (format) {
            case ABB        -> renderAbb(run, rows);
            case KAPITAL    -> renderKapital(run, rows);
            case PASHA      -> renderPasha(run, rows);
            case RESPUBLIKA -> renderRespublika(run, rows);
            default         -> renderGenericCsv(run, rows);
        };
    }

    // ── Per-bank renderers ───────────────────────────────────────────────────

    /**
     * ABB Bank Azerbaijan salary-disbursement CSV.
     * Columns: Sıra №,Müştəri adı,VÖEN,Hesab,IBAN,Məbləğ,Valyuta,Məqsəd
     */
    private String renderAbb(PayrollRun run, java.util.List<RowData> rows) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("Sıra №,Müştəri adı,VÖEN,Hesab,IBAN,Məbləğ,Valyuta,Məqsəd\n");
        int seq = 1;
        for (RowData r : rows) {
            sb.append(seq++).append(',')
              .append(q(r.fullName)).append(',')
              .append(q(r.taxId)).append(',')
              .append(q(r.account)).append(',')
              .append(q(r.iban)).append(',')
              .append(r.amount).append(',')
              .append(run.getCurrency()).append(',')
              .append(q("Əmək haqqı " + run.getPeriodYear() + "/"
                      + String.format("%02d", run.getPeriodMonth())))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * Kapital Bank Azerbaijan salary CSV (semicolon-delimited).
     * Columns: N;MFID;ACCOUNT_NO;IBAN;FULL_NAME;AMOUNT;CURRENCY;PURPOSE_CODE;DESCRIPTION
     */
    private String renderKapital(PayrollRun run, java.util.List<RowData> rows) {
        String period = run.getPeriodYear() + String.format("%02d", run.getPeriodMonth());
        StringBuilder sb = new StringBuilder(4096);
        sb.append("N;MFID;ACCOUNT_NO;IBAN;FULL_NAME;AMOUNT;CURRENCY;PURPOSE_CODE;DESCRIPTION\n");
        int seq = 1;
        for (RowData r : rows) {
            sb.append(seq++).append(';')
              .append(ns(r.bankCode)).append(';')
              .append(ns(r.account)).append(';')
              .append(ns(r.iban)).append(';')
              .append(qs(r.fullName)).append(';')
              .append(r.amount).append(';')
              .append(run.getCurrency()).append(';')
              .append("10").append(';')   // purpose code 10 = salary
              .append(qs("Emek haqqi " + period))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * PASHA Bank Azerbaijan salary-disbursement CSV.
     * Columns: ROW_ID,EMPLOYEE_CODE,EMPLOYEE_NAME,IBAN,ACCOUNT,BANK_CODE,NET_AMOUNT,CURRENCY,PERIOD,PAYSLIP
     */
    private String renderPasha(PayrollRun run, java.util.List<RowData> rows) {
        String period = run.getPeriodYear() + "/" + String.format("%02d", run.getPeriodMonth());
        StringBuilder sb = new StringBuilder(4096);
        sb.append("ROW_ID,EMPLOYEE_CODE,EMPLOYEE_NAME,IBAN,ACCOUNT,BANK_CODE,NET_AMOUNT,CURRENCY,PERIOD,PAYSLIP\n");
        int seq = 1;
        for (RowData r : rows) {
            sb.append(seq++).append(',')
              .append(q(r.employeeNo)).append(',')
              .append(q(r.fullName)).append(',')
              .append(q(r.iban)).append(',')
              .append(q(r.account)).append(',')
              .append(q(r.bankCode)).append(',')
              .append(r.amount).append(',')
              .append(run.getCurrency()).append(',')
              .append(q(period)).append(',')
              .append(q(r.payslipNo))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * Bank Respublika Azerbaijan salary-disbursement (tab-delimited TXT).
     * Columns: No\tFull_Name\tAccount\tIBAN\tAmount\tCurrency\tPeriod
     */
    private String renderRespublika(PayrollRun run, java.util.List<RowData> rows) {
        String period = run.getPeriodYear() + String.format("%02d", run.getPeriodMonth());
        StringBuilder sb = new StringBuilder(4096);
        sb.append("No\tFull_Name\tAccount\tIBAN\tAmount\tCurrency\tPeriod\n");
        int seq = 1;
        for (RowData r : rows) {
            sb.append(seq++).append('\t')
              .append(r.fullName).append('\t')
              .append(ns(r.account)).append('\t')
              .append(ns(r.iban)).append('\t')
              .append(r.amount).append('\t')
              .append(run.getCurrency()).append('\t')
              .append(period)
              .append('\n');
        }
        return sb.toString();
    }

    private String renderGenericCsv(PayrollRun run, java.util.List<RowData> rows) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("# Bank payment file\n");
        sb.append("# Run: ").append(run.getRunNo())
          .append("  Period: ").append(run.getPeriodYear()).append('/')
          .append(String.format("%02d", run.getPeriodMonth()))
          .append("  Currency: ").append(run.getCurrency()).append('\n');
        sb.append("payslip_no,employee_no,full_name,iban,account_number,bank_code,amount,currency\n");
        for (RowData r : rows) {
            sb.append(q(r.payslipNo)).append(',')
              .append(q(r.employeeNo)).append(',')
              .append(q(r.fullName)).append(',')
              .append(q(r.iban)).append(',')
              .append(q(r.account)).append(',')
              .append(q(r.bankCode)).append(',')
              .append(r.amount).append(',')
              .append(run.getCurrency())
              .append('\n');
        }
        return sb.toString();
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private record RowData(String payslipNo, String employeeNo, String fullName,
                            String iban, String account, String bankCode,
                            String taxId, java.math.BigDecimal amount) {}

    private PayrollRun requireApprovedRun(UUID runId) {
        PayrollRun run = runs.findById(runId)
                .orElseThrow(() -> new BadRequestException("Run not found: " + runId));
        if (run.getStatus() != PayrollRunStatus.APPROVED
                && run.getStatus() != PayrollRunStatus.PAID
                && run.getStatus() != PayrollRunStatus.CLOSED) {
            throw new BadRequestException(
                    "Bank-file export requires APPROVED, PAID, or CLOSED. Current: " + run.getStatus());
        }
        return run;
    }

    private java.util.List<RowData> buildRows(PayrollRun run) {
        java.util.List<RowData> rows = new java.util.ArrayList<>();
        for (PayrollResult r : results.findByRunIdOrderByEmployeeIdAsc(run.getId())) {
            Employee emp = employees.findById(r.getEmployeeId()).orElse(null);
            BankAccount ba = bankAccounts.findByEmployeeId(r.getEmployeeId()).orElse(null);
            rows.add(new RowData(
                    ns(r.getPayslipNo()),
                    emp == null ? "" : emp.getEmployeeNo(),
                    emp == null ? "" : (emp.getLastName() + " " + emp.getFirstName()).trim(),
                    ba == null ? "" : ns(ba.getIban()),
                    ba == null ? "" : ns(ba.getAccountNumber()),
                    ba == null ? "" : ns(ba.getBankCode()),
                    emp == null ? "" : "",   // taxId — stored on employee in a later milestone
                    r.getNetAmount()
            ));
        }
        return rows;
    }

    /** Null-safe string. */
    private String ns(String s) { return s == null ? "" : s; }

    /** CSV-quoted string (comma delimiter). */
    private String q(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Semicolon-safe quoted string (Kapital uses ; delimiter). */
    private String qs(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Legacy entry point — delegates to the generic CSV renderer. */
    @Transactional(readOnly = true)
    public String exportCsv(UUID runId) {
        return renderBankFile(runId, BankFileFormat.CSV);
    }
}
