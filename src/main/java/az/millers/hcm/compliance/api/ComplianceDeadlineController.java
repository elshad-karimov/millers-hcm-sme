package az.millers.hcm.compliance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compliance.domain.ComplianceDeadline;
import az.millers.hcm.compliance.service.ComplianceDeadlineService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M470 — Compliance deadline endpoints (HR_ADMIN).
 */
@RestController
@RequestMapping("/api/compliance/deadlines")
public class ComplianceDeadlineController {

    private final ComplianceDeadlineService service;

    public ComplianceDeadlineController(ComplianceDeadlineService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<ComplianceDeadline>> list() {
        return ResponseEntity.ok(service.listActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<ComplianceDeadline> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<ComplianceDeadline> create(@RequestBody ComplianceDeadline deadline) {
        return ResponseEntity.ok(service.create(deadline));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<ComplianceDeadline> update(@PathVariable UUID id,
                                                      @RequestBody ComplianceDeadline deadline) {
        return ResponseEntity.ok(service.update(id, deadline));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * M470 — Get upcoming deadlines within the next X days (default 30).
     */
    @GetMapping("/upcoming")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<ComplianceDeadlineService.UpcomingDeadline>> upcoming(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.getUpcoming(days));
    }
}
