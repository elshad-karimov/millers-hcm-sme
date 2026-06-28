package az.millers.hcm.leave.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.AbsenceConvertRequest;
import az.millers.hcm.leave.api.dto.AbsenceDismissRequest;
import az.millers.hcm.leave.api.dto.AbsenceScanResult;
import az.millers.hcm.leave.service.UnauthorizedAbsenceService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/unauthorized-absences")
public class UnauthorizedAbsenceController {

    private final UnauthorizedAbsenceService service;

    public UnauthorizedAbsenceController(UnauthorizedAbsenceService service) {
        this.service = service;
    }

    /** Pending absences across all employees (HR workspace view). */
    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AbsenceScanResult> listPending() {
        return service.listPending();
    }

    /** Scan a specific employee's absences for a date range. */
    @GetMapping("/scan")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public List<AbsenceScanResult> scan(
            @RequestParam UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.scan(employeeId, from, to);
    }

    /** Convert selected absence dates to an approved leave request. */
    @PostMapping("/convert")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public List<AbsenceScanResult> convert(@Valid @RequestBody AbsenceConvertRequest req) {
        return service.convert(req);
    }

    /** Dismiss selected absence dates without leave conversion (record the HR decision). */
    @PostMapping("/dismiss")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public List<AbsenceScanResult> dismiss(@Valid @RequestBody AbsenceDismissRequest req) {
        return service.dismiss(req);
    }
}
