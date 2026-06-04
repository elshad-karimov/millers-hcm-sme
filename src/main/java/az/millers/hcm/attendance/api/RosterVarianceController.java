package az.millers.hcm.attendance.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.api.dto.VarianceDtos.VarianceReport;
import az.millers.hcm.attendance.service.RosterVarianceService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M113 — Roster variance dashboard.
 *
 * <p>HR_PLUS_MANAGERS read; the underlying service applies the ABAC
 * scope filter, so a department manager only sees their own team.
 */
@RestController
@RequestMapping("/api/attendance/variance")
public class RosterVarianceController {

    private final RosterVarianceService service;

    public RosterVarianceController(RosterVarianceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public VarianceReport report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
