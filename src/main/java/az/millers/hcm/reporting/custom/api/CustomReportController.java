package az.millers.hcm.reporting.custom.api;

import java.util.ArrayList;
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

import az.millers.hcm.reporting.custom.CustomReportSource;
import az.millers.hcm.reporting.custom.FieldSpec;
import az.millers.hcm.reporting.custom.FilterOp;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.CatalogResponse;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.CustomReportDetail;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.CustomReportSummary;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.FieldCatalog;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.OpCatalog;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.RunResponse;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.SaveRequest;
import az.millers.hcm.reporting.custom.api.CustomReportDtos.SourceCatalog;
import az.millers.hcm.reporting.custom.service.CustomReportService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M119 — REST surface for the custom report builder. Role gate is the
 * same as the rest of the reporting module ({@link SecurityRoles#READ_REPORTS})
 * — HR + managers can author their own, save as shared, and run. ABAC
 * scoping is enforced inside the service via {@code AccessScopeService}.
 */
@RestController
@RequestMapping("/api/custom-reports")
public class CustomReportController {

    private final CustomReportService service;

    public CustomReportController(CustomReportService service) {
        this.service = service;
    }

    /**
     * Source + field + operator catalogue. The SPA loads this once to
     * populate the source picker, field checklist, and op dropdowns.
     */
    @GetMapping("/catalog")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public CatalogResponse catalog() {
        List<SourceCatalog> sources = new ArrayList<>();
        for (CustomReportSource s : CustomReportSource.values()) {
            List<FieldCatalog> fields = new ArrayList<>(s.fields().size());
            for (FieldSpec f : s.fields()) {
                fields.add(new FieldCatalog(
                        f.key(), f.label(), f.type(), f.filterable(), f.sortable()));
            }
            sources.add(new SourceCatalog(
                    s.name(),
                    s.label(),
                    s.scopeEmployeeIdExpr() == null ? "GLOBAL" : "EMPLOYEE",
                    fields));
        }
        List<OpCatalog> ops = new ArrayList<>(FilterOp.values().length);
        for (FilterOp op : FilterOp.values()) {
            List<az.millers.hcm.reporting.custom.FieldType> compat = new ArrayList<>();
            for (az.millers.hcm.reporting.custom.FieldType t :
                    az.millers.hcm.reporting.custom.FieldType.values()) {
                if (op.isCompatible(t)) compat.add(t);
            }
            ops.add(new OpCatalog(op, op.valueCount(), compat));
        }
        return new CatalogResponse(sources, ops);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public List<CustomReportSummary> list() {
        return service.listVisible();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public CustomReportDetail get(@PathVariable UUID id) {
        return service.getDetail(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public CustomReportDetail create(@RequestBody SaveRequest req) {
        return service.save(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public CustomReportDetail update(@PathVariable UUID id, @RequestBody SaveRequest req) {
        // PUT is a convenience — saving the same (owner, name) overwrites.
        // We honour the path id only as a "are you sure" hint; the canonical
        // upsert is (owner, name).
        return service.save(req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public RunResponse runSaved(@PathVariable UUID id) {
        return service.runSaved(id);
    }

    @PostMapping("/preview")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public RunResponse runPreview(@RequestBody SaveRequest req) {
        return service.runPreview(req);
    }
}
