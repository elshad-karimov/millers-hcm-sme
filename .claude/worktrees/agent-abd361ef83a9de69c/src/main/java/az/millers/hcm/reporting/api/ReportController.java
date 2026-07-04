package az.millers.hcm.reporting.api;

import az.millers.hcm.security.SecurityRoles;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.ReportDtos.AttendanceReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.AttritionReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.HeadcountReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.LeaveReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.PayrollSummaryReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.PerformanceReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.RecruitmentReport;
import az.millers.hcm.reporting.api.dto.ReportDtos.TrainingReport;
import az.millers.hcm.reporting.service.ReportService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/headcount")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public HeadcountReport headcount() {
        return service.headcount();
    }

    @GetMapping("/attrition")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public AttritionReport attrition(@RequestParam(required = false) Integer year) {
        return service.attrition(year);
    }

    @GetMapping("/payroll-summary")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER','DEPARTMENT_MANAGER')")
    public PayrollSummaryReport payrollSummary(@RequestParam(required = false) Integer year) {
        return service.payrollSummary(year);
    }

    @GetMapping("/leave")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public LeaveReport leave(@RequestParam(required = false) Integer year) {
        return service.leaveUsage(year);
    }

    @GetMapping("/attendance")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public AttendanceReport attendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.attendance(from, to);
    }

    @GetMapping("/training")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public TrainingReport training() {
        return service.trainingCompliance();
    }

    @GetMapping("/performance")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PerformanceReport performance(@RequestParam(required = false) UUID cycleId) {
        return service.performance(cycleId);
    }

    @GetMapping("/recruitment")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public RecruitmentReport recruitment() {
        return service.recruitment();
    }
}
