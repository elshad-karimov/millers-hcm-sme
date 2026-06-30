package az.millers.hcm.payroll.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.*;
import az.millers.hcm.payroll.service.PayrollReportSuiteService;
import az.millers.hcm.payroll.service.PayrollVarianceService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/payroll/reports")
public class PayrollReportsController {

    private final PayrollVarianceService varianceService;
    private final PayrollReportSuiteService reportService;

    public PayrollReportsController(PayrollVarianceService varianceService,
                                    PayrollReportSuiteService reportService) {
        this.varianceService = varianceService;
        this.reportService = reportService;
    }

    /**
     * M353: Payroll variance report between two REGULAR runs.
     */
    @GetMapping("/variance")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PayrollVarianceResponse variance(@RequestParam UUID currentRunId,
                                             @RequestParam UUID priorRunId) {
        return varianceService.variance(currentRunId, priorRunId);
    }

    /**
     * M353: Year-to-date summary for PAID REGULAR runs.
     */
    @GetMapping("/ytd")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public YtdSummaryResponse ytd(@RequestParam int year,
                                   @RequestParam(required = false) UUID employeeId) {
        return varianceService.ytd(year, employeeId);
    }

    /**
     * M358: Period summary with employee breakdown and components.
     */
    @GetMapping("/period-summary")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PeriodSummaryResponse periodSummary(@RequestParam UUID runId) {
        return reportService.periodSummary(runId);
    }

    /**
     * M358: Employer cost report (gross + employer contributions).
     */
    @GetMapping("/employer-cost")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public EmployerCostReportResponse employerCost(@RequestParam UUID runId) {
        return reportService.employerCost(runId);
    }

    /**
     * M358: Active loans and pending advances status.
     */
    @GetMapping("/loan-advance-status")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public LoanAdvanceStatusResponse loanAdvanceStatus() {
        return reportService.loanAdvanceStatus();
    }

    /**
     * M358: Bank reconciliation report (payroll vs bank file totals).
     */
    @GetMapping("/bank-reconciliation")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public BankReconciliationResponse bankReconciliation(@RequestParam UUID runId) {
        return reportService.bankReconciliation(runId);
    }
}
