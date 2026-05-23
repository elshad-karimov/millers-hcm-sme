package az.millers.hcm.reporting.api;

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
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public HeadcountReport headcount() {
        return service.headcount();
    }

    @GetMapping("/attrition")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public AttritionReport attrition(@RequestParam(required = false) Integer year) {
        return service.attrition(year);
    }

    @GetMapping("/payroll-summary")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER','DEPARTMENT_MANAGER')")
    public PayrollSummaryReport payrollSummary(@RequestParam(required = false) Integer year) {
        return service.payrollSummary(year);
    }

    @GetMapping("/leave")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public LeaveReport leave(@RequestParam(required = false) Integer year) {
        return service.leaveUsage(year);
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public AttendanceReport attendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.attendance(from, to);
    }

    @GetMapping("/training")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public TrainingReport training() {
        return service.trainingCompliance();
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public PerformanceReport performance(@RequestParam(required = false) UUID cycleId) {
        return service.performance(cycleId);
    }

    @GetMapping("/recruitment")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER')")
    public RecruitmentReport recruitment() {
        return service.recruitment();
    }
}
