package az.millers.hcm.reporting.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.PayrollReportDtos.BonusReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.DeductionReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.FinalSettlementReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.OvertimeReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.PayrollVarianceReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.SocialInsuranceReport;
import az.millers.hcm.reporting.api.dto.PayrollReportDtos.TaxReport;
import az.millers.hcm.reporting.service.PayrollReportService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Payroll-specific report endpoints (M225 / PRD §8.9.9).
 *
 * <p>All endpoints require {@code READ_PAYROLL} — i.e., SYSTEM_ADMIN,
 * HR_ADMIN, HR_SPECIALIST, PAYROLL_SPECIALIST, AUDITOR, or FINANCE_USER.
 *
 * <ul>
 *   <li>Run-scoped reports (tax, social-insurance, deductions, bonuses,
 *       overtime) accept a {@code runId} query parameter.</li>
 *   <li>Year-scoped reports (final-settlement, variance) accept a
 *       {@code year} parameter; defaults to the current calendar year.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reports/payroll")
public class PayrollReportController {

    private final PayrollReportService service;

    public PayrollReportController(PayrollReportService service) {
        this.service = service;
    }

    @GetMapping("/tax")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public TaxReport taxReport(@RequestParam UUID runId) {
        return service.taxReport(runId);
    }

    @GetMapping("/social-insurance")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public SocialInsuranceReport socialInsuranceReport(@RequestParam UUID runId) {
        return service.socialInsuranceReport(runId);
    }

    @GetMapping("/deductions")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public DeductionReport deductionReport(@RequestParam UUID runId) {
        return service.deductionReport(runId);
    }

    @GetMapping("/bonuses")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public BonusReport bonusReport(@RequestParam UUID runId) {
        return service.bonusReport(runId);
    }

    @GetMapping("/overtime")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public OvertimeReport overtimeReport(@RequestParam UUID runId) {
        return service.overtimeReport(runId);
    }

    @GetMapping("/final-settlement")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public FinalSettlementReport finalSettlementReport(
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : java.time.Year.now().getValue();
        return service.finalSettlementReport(y);
    }

    @GetMapping("/variance")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PayrollVarianceReport varianceReport(
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : java.time.Year.now().getValue();
        return service.varianceReport(y);
    }
}
