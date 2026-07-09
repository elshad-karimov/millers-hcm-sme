package az.millers.hcm.compliance.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compliance.service.WorkAuthorizationService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M471 — Work authorization expiry tracking (HR_ADMIN).
 */
@RestController
@RequestMapping("/api/compliance/work-authorization")
public class WorkAuthorizationController {

    private final WorkAuthorizationService service;

    public WorkAuthorizationController(WorkAuthorizationService service) {
        this.service = service;
    }

    @GetMapping("/expiring")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<WorkAuthorizationService.ExpiringWorkAuth>> expiring(
            @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(service.getExpiring(days));
    }
}
