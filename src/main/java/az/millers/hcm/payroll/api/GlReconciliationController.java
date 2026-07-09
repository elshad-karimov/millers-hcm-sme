package az.millers.hcm.payroll.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.service.GlReconciliationService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M467 — GL reconciliation: compare payroll vs posted GL journals.
 */
@RestController
@RequestMapping("/api/reports/gl-reconciliation")
public class GlReconciliationController {

    private final GlReconciliationService service;

    public GlReconciliationController(GlReconciliationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<GlReconciliationService.ReconciliationReport> reconcile(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(service.reconcile(year, month));
    }
}
