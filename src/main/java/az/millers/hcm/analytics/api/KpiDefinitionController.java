package az.millers.hcm.analytics.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.analytics.domain.KpiDefinition;
import az.millers.hcm.analytics.service.KpiDefinitionService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M473 — KPI definition CRUD (HR_ADMIN only).
 */
@RestController
@RequestMapping("/api/analytics/kpi-definitions")
public class KpiDefinitionController {

    private final KpiDefinitionService service;

    public KpiDefinitionController(KpiDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<KpiDefinition> listAll(@RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        return activeOnly ? service.listActive() : service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public KpiDefinition get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public KpiDefinition create(@RequestBody KpiDefinition kpi) {
        return service.create(kpi);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public KpiDefinition update(@PathVariable UUID id, @RequestBody KpiDefinition kpi) {
        return service.update(id, kpi);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
