package az.millers.hcm.compliance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compliance.domain.StatutoryReportSubmission;
import az.millers.hcm.compliance.domain.StatutoryReportSubmission.SubmissionStatus;
import az.millers.hcm.compliance.service.StatutoryReportSubmissionService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M469 — Statutory report submission endpoints (HR_ADMIN).
 */
@RestController
@RequestMapping("/api/compliance/statutory-submissions")
public class StatutoryReportSubmissionController {

    private final StatutoryReportSubmissionService service;

    public StatutoryReportSubmissionController(StatutoryReportSubmissionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<StatutoryReportSubmission>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<StatutoryReportSubmission> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<StatutoryReportSubmission> create(
            @RequestParam UUID templateId,
            @RequestParam LocalDate periodStart,
            @RequestParam LocalDate periodEnd) {
        return ResponseEntity.ok(service.create(templateId, periodStart, periodEnd));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<StatutoryReportSubmission> generate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.generate(id));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<StatutoryReportSubmission> updateStatus(
            @PathVariable UUID id,
            @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request.status(), request.responseNotes()));
    }

    public record StatusUpdateRequest(SubmissionStatus status, String responseNotes) {}
}
