package az.millers.hcm.reporting.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ActivityFeed;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.CertificationExpiringReport;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ContractExpiringReport;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.EmpMgmtSummary;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ProbationDueReport;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.RehireReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.SpanOfControlReport;
import az.millers.hcm.reporting.service.ActivityFeedService;
import az.millers.hcm.reporting.service.EmpMgmtReportsService;
import az.millers.hcm.reporting.service.SpanOfControlService;

/**
 * Employee-Management report family + global activity feed (M80 / P2-29-33).
 * Sits under {@code /api/reports/emp-mgmt} so the existing
 * {@link ReportController} URL family stays intact.
 */
@RestController
@RequestMapping("/api/reports/emp-mgmt")
public class EmpMgmtReportsController {

    private static final String READ_ROLES =
            "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')";

    private final EmpMgmtReportsService reports;
    private final ActivityFeedService activity;
    private final SpanOfControlService spanOfControl;

    public EmpMgmtReportsController(EmpMgmtReportsService reports,
                                     ActivityFeedService activity,
                                     SpanOfControlService spanOfControl) {
        this.reports = reports;
        this.activity = activity;
        this.spanOfControl = spanOfControl;
    }

    @GetMapping("/span-of-control")
    @PreAuthorize(READ_ROLES)
    public SpanOfControlReport spanOfControl() {
        return spanOfControl.report();
    }

    @GetMapping("/summary")
    @PreAuthorize(READ_ROLES)
    public EmpMgmtSummary summary() {
        return reports.summary();
    }

    @GetMapping("/probation-due")
    @PreAuthorize(READ_ROLES)
    public ProbationDueReport probationDue(@RequestParam(required = false) Integer lookaheadDays) {
        return reports.probationDue(lookaheadDays);
    }

    @GetMapping("/contracts-expiring")
    @PreAuthorize(READ_ROLES)
    public ContractExpiringReport contractsExpiring(@RequestParam(required = false) Integer lookaheadDays) {
        return reports.contractsExpiring(lookaheadDays);
    }

    @GetMapping("/certifications-expiring")
    @PreAuthorize(READ_ROLES)
    public CertificationExpiringReport certsExpiring(@RequestParam(required = false) Integer lookaheadDays) {
        return reports.certificationsExpiring(lookaheadDays);
    }

    @GetMapping("/recent-rehires")
    @PreAuthorize(READ_ROLES)
    public RehireReport recentRehires(@RequestParam(required = false) Integer limit) {
        return reports.recentRehires(limit);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','AUDITOR')")
    public ActivityFeed activity(@RequestParam(required = false) String module,
                                  @RequestParam(required = false) String entityName,
                                  @RequestParam(required = false) String actor,
                                  @RequestParam(required = false) Integer limit) {
        return activity.recent(module, entityName, actor, limit);
    }
}
