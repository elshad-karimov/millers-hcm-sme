package az.millers.hcm.employeerelations.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.employeerelations.domain.WarningLevel;
import az.millers.hcm.employeerelations.domain.WarningRecord;
import az.millers.hcm.employeerelations.service.WarningService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M446 — Warning controller.
 */
@RestController
@RequestMapping("/api/er/warnings")
public class WarningController {

    private final WarningService service;

    public WarningController(WarningService service) {
        this.service = service;
    }

    public record IssueWarningRequest(
            UUID employeeId,
            WarningLevel level,
            String reason,
            UUID disciplinaryActionId,
            UUID attachmentId) {}

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public WarningRecord issue(@RequestBody IssueWarningRequest req) {
        return service.issue(req.employeeId(), req.level(), req.reason(),
                req.disciplinaryActionId(), req.attachmentId());
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<WarningRecord> listByEmployee(@PathVariable UUID employeeId) {
        return service.listByEmployee(employeeId);
    }

    @GetMapping("/employees/{employeeId}/active")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<WarningRecord> activeWarnings(@PathVariable UUID employeeId) {
        return service.activeWarnings(employeeId);
    }
}
