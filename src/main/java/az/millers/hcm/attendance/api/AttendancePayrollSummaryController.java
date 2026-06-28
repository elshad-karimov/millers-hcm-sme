package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.domain.AttendancePayrollSummary;
import az.millers.hcm.attendance.service.AttendancePayrollSummaryService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M331: Attendance–payroll summary endpoints.
 */
@RestController
@RequestMapping("/api/attendance/payroll-summary")
public class AttendancePayrollSummaryController {

    private final AttendancePayrollSummaryService service;

    public AttendancePayrollSummaryController(AttendancePayrollSummaryService service) {
        this.service = service;
    }

    /** Returns the per-employee attendance deduction breakdown for a payroll run. */
    @GetMapping("/{runId}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AttendancePayrollSummary> getByRun(@PathVariable UUID runId) {
        return service.getByRun(runId);
    }

    /** Recompute attendance summary for a run (e.g. after late correction approval). */
    @PostMapping("/{runId}/recompute")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public List<AttendancePayrollSummary> recompute(@PathVariable UUID runId) {
        return service.compute(runId);
    }
}
