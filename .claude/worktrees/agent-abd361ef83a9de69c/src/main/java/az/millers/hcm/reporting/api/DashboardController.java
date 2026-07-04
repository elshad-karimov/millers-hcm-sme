package az.millers.hcm.reporting.api;

import az.millers.hcm.security.SecurityRoles;

import java.time.Year;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.DashboardMvRefreshJob;
import az.millers.hcm.reporting.api.dto.DashboardDtos.ExecutiveDashboard;
import az.millers.hcm.reporting.api.dto.DashboardDtos.HeadcountTrend;
import az.millers.hcm.reporting.api.dto.DashboardDtos.ManagerTeamDashboard;
import az.millers.hcm.reporting.api.dto.DashboardDtos.PayrollTrend;
import az.millers.hcm.reporting.api.dto.DashboardDtos.TurnoverDashboard;
import az.millers.hcm.reporting.service.DashboardService;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * Analytics dashboard endpoints backing the 11 PRD dashboards (Section 10.3).
 *
 * <p>All endpoints return time-series and breakdown data suited for
 * Recharts visualisations in the SPA. Access is role-gated:
 * <ul>
 *   <li>General (headcount, payroll, turnover) — HR_SPECIALIST or above</li>
 *   <li>Manager team — DEPARTMENT_MANAGER (their own team only)</li>
 *   <li>Executive — SYSTEM_ADMIN, HR_ADMIN, EXECUTIVE (Finance User)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dashboards")
public class DashboardController {

    private final DashboardService svc;
    private final EmployeeContextService ctx;
    private final DashboardMvRefreshJob refreshJob;

    public DashboardController(DashboardService svc, EmployeeContextService ctx,
                                DashboardMvRefreshJob refreshJob) {
        this.svc = svc;
        this.ctx = ctx;
        this.refreshJob = refreshJob;
    }

    // ------------------------------------------------------------------ //
    //  Headcount trend  (HR Dashboard + Headcount Dashboard)             //
    // ------------------------------------------------------------------ //

    @GetMapping("/headcount-trend")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public HeadcountTrend headcountTrend(
            @RequestParam(defaultValue = "12") int months) {
        return svc.headcountTrend(Math.min(months, 24));
    }

    // ------------------------------------------------------------------ //
    //  Payroll trend  (Payroll Dashboard)                                //
    // ------------------------------------------------------------------ //

    @GetMapping("/payroll-trend")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','PAYROLL_SPECIALIST','FINANCE_USER','AUDITOR')")
    public PayrollTrend payrollTrend(
            @RequestParam(defaultValue = "0") int year) {
        int y = year == 0 ? Year.now().getValue() : year;
        return svc.payrollTrend(y);
    }

    // ------------------------------------------------------------------ //
    //  Turnover dashboard                                                 //
    // ------------------------------------------------------------------ //

    @GetMapping("/turnover")
    @PreAuthorize(SecurityRoles.READ_HR)
    public TurnoverDashboard turnover(
            @RequestParam(defaultValue = "0") int year) {
        int y = year == 0 ? Year.now().getValue() : year;
        return svc.turnover(y);
    }

    // ------------------------------------------------------------------ //
    //  Manager team dashboard                                             //
    // ------------------------------------------------------------------ //

    @GetMapping("/manager-team")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER')")
    public ManagerTeamDashboard managerTeam() {
        UUID managerId = ctx.currentEmployee().getId();
        return svc.managerTeam(managerId);
    }

    // ------------------------------------------------------------------ //
    //  Executive dashboard                                                //
    // ------------------------------------------------------------------ //

    @GetMapping("/executive")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','FINANCE_USER','AUDITOR')")
    public ExecutiveDashboard executive() {
        return svc.executive();
    }

    // ------------------------------------------------------------------ //
    //  M177 — On-demand materialized-view refresh                        //
    // ------------------------------------------------------------------ //

    /**
     * Forces an immediate non-concurrent refresh of all dashboard MVs.
     * Useful after a large batch operation (payroll run, bulk import) so
     * dashboards reflect the new data without waiting for the nightly job.
     * Restricted to System Admin and HR Admin.
     */
    @PostMapping("/refresh-mvs")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
    public void refreshMvs() {
        refreshJob.refreshAll();
    }
}
