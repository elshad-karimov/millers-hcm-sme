package az.millers.hcm.attendance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.service.AttendanceReportService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M336: Attendance report controller.
 */
@RestController
@RequestMapping("/api/reports/attendance")
public class AttendanceReportController {

    private final AttendanceReportService service;
    private final CurrentRequest currentRequest;

    public AttendanceReportController(AttendanceReportService service, CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @GetMapping("/{type}")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public List<Map<String, Object>> getReport(
            @PathVariable String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID departmentId) {
        return service.getReport(type, from, to, departmentId);
    }
}
