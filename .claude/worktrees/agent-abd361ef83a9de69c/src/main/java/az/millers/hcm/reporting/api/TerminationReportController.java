package az.millers.hcm.reporting.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.TerminationReportDtos.DepartmentTurnoverReport;
import az.millers.hcm.reporting.api.dto.TerminationReportDtos.ExitInterviewAnalysisReport;
import az.millers.hcm.reporting.api.dto.TerminationReportDtos.TerminationByReasonReport;
import az.millers.hcm.reporting.service.TerminationReportService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Termination-module report endpoints (M226 / PRD §8.11.5).
 *
 * <p>Base path: {@code /api/reports/termination}
 */
@RestController
@RequestMapping("/api/reports/termination")
public class TerminationReportController {

    private final TerminationReportService service;

    public TerminationReportController(TerminationReportService service) {
        this.service = service;
    }

    @GetMapping("/by-reason")
    @PreAuthorize(SecurityRoles.READ_HR)
    public TerminationByReasonReport byReason(
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : java.time.Year.now().getValue();
        return service.byReason(y);
    }

    @GetMapping("/department-turnover")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public DepartmentTurnoverReport departmentTurnover(
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : java.time.Year.now().getValue();
        return service.departmentTurnover(y);
    }

    @GetMapping("/exit-interview-analysis")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ExitInterviewAnalysisReport exitInterviewAnalysis(
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : java.time.Year.now().getValue();
        return service.exitInterviewAnalysis(y);
    }
}
