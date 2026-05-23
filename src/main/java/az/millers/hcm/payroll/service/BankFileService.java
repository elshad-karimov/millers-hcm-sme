package az.millers.hcm.payroll.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.BankAccount;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.repo.BankAccountRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;

/**
 * Generates a generic bank-file CSV (PRD 8.9.6).
 *
 * <p>Per-bank templates (ABB / Kapital / PASHA / Respublika) are configured
 * as plug-ins in a follow-up milestone; the column shape here is a sane
 * neutral baseline that includes the audit-relevant fields.
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

    @Transactional(readOnly = true)
    public String exportCsv(UUID runId) {
        PayrollRun run = runs.findById(runId)
                .orElseThrow(() -> new BadRequestException("Run not found: " + runId));
        if (run.getStatus() != PayrollRunStatus.APPROVED
                && run.getStatus() != PayrollRunStatus.PAID
                && run.getStatus() != PayrollRunStatus.CLOSED) {
            throw new BadRequestException(
                    "Bank-file export requires APPROVED, PAID, or CLOSED. Current: " + run.getStatus());
        }
        StringBuilder out = new StringBuilder(4096);
        out.append("# Bank payment file for run ").append(run.getRunNo()).append('\n');
        out.append("# Period: ").append(run.getPeriodYear()).append('/')
                .append(String.format("%02d", run.getPeriodMonth()))
                .append("    Currency: ").append(run.getCurrency())
                .append("    Status: ").append(run.getStatus())
                .append('\n');
        out.append("payslip_no,employee_no,full_name,iban,account_number,bank_code,amount,currency\n");
        for (PayrollResult r : results.findByRunIdOrderByEmployeeIdAsc(run.getId())) {
            Employee emp = employees.findById(r.getEmployeeId()).orElse(null);
            BankAccount ba = bankAccounts.findByEmployeeId(r.getEmployeeId()).orElse(null);
            String empNo = emp == null ? "" : emp.getEmployeeNo();
            String fullName = emp == null
                    ? ""
                    : (emp.getLastName() + " " + emp.getFirstName()).trim();
            String iban = ba == null ? "" : nullSafe(ba.getIban());
            String account = ba == null ? "" : nullSafe(ba.getAccountNumber());
            String bankCode = ba == null ? "" : nullSafe(ba.getBankCode());
            out.append(quote(r.getPayslipNo())).append(',')
                    .append(quote(empNo)).append(',')
                    .append(quote(fullName)).append(',')
                    .append(quote(iban)).append(',')
                    .append(quote(account)).append(',')
                    .append(quote(bankCode)).append(',')
                    .append(r.getNetAmount().toPlainString()).append(',')
                    .append(run.getCurrency())
                    .append('\n');
        }
        return out.toString();
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private String quote(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
