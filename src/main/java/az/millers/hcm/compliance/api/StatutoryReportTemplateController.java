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
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compliance.domain.StatutoryReportTemplate;
import az.millers.hcm.compliance.service.StatutoryReportTemplateService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M468 — Statutory report template endpoints (HR_ADMIN).
 */
@RestController
@RequestMapping("/api/compliance/statutory-report-templates")
public class StatutoryReportTemplateController {

    private final StatutoryReportTemplateService service;

    public StatutoryReportTemplateController(StatutoryReportTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<StatutoryReportTemplate>> list() {
        return ResponseEntity.ok(service.listActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<StatutoryReportTemplate> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<StatutoryReportTemplate> create(@RequestBody StatutoryReportTemplate template) {
        return ResponseEntity.ok(service.create(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<StatutoryReportTemplate> update(@PathVariable UUID id,
                                                          @RequestBody StatutoryReportTemplate template) {
        return ResponseEntity.ok(service.update(id, template));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
