package az.millers.hcm.organization.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.organization.api.dto.OrgTreeNode;
import az.millers.hcm.organization.api.dto.OrgUnitRequest;
import az.millers.hcm.organization.api.dto.OrgUnitResponse;
import az.millers.hcm.organization.api.dto.RollbackRequest;
import az.millers.hcm.organization.api.dto.StatusTransitionRequest;
import az.millers.hcm.organization.api.dto.StructureVersionRequest;
import az.millers.hcm.organization.api.dto.StructureVersionResponse;
import az.millers.hcm.organization.service.OrgChartSvgRenderer;
import az.millers.hcm.organization.service.OrgStructureService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/org")
public class OrgStructureController {

    private final OrgStructureService service;
    private final OrgChartSvgRenderer chartRenderer;

    public OrgStructureController(OrgStructureService service,
                                   OrgChartSvgRenderer chartRenderer) {
        this.service = service;
        this.chartRenderer = chartRenderer;
    }

    /**
     * M83 — Org chart SVG download. Renders the version's tree top-down with
     * a layered layout, returns a self-contained SVG (no external assets).
     */
    @GetMapping(value = "/versions/{id}/chart.svg",
            produces = "image/svg+xml")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public ResponseEntity<String> chartSvg(@PathVariable UUID id) {
        OrgTreeNode root = service.buildTree(id);
        String title = "Org chart — v" + service.getVersion(id).getVersionNumber();
        String svg = chartRenderer.render(root, title);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .header("Content-Disposition",
                        "attachment; filename=\"org-chart-v"
                                + service.getVersion(id).getVersionNumber() + ".svg\"")
                .body(svg);
    }

    // ---------- Versions ----------

    @GetMapping("/versions")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR')")
    public List<StructureVersionResponse> listVersions() {
        return service.listVersions().stream().map(StructureVersionResponse::from).toList();
    }

    @GetMapping("/versions/active")
    @PreAuthorize("isAuthenticated()")
    public StructureVersionResponse active() {
        return service.getActiveVersion()
                .map(StructureVersionResponse::from)
                .orElse(null);
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR')")
    public StructureVersionResponse getVersion(@PathVariable UUID id) {
        return StructureVersionResponse.from(service.getVersion(id));
    }

    @PostMapping("/versions/draft")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public StructureVersionResponse createDraft(@Valid @RequestBody StructureVersionRequest req) {
        return StructureVersionResponse.from(service.createDraft(req));
    }

    @PostMapping("/versions/{id}/submit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public StructureVersionResponse submit(@PathVariable UUID id) {
        return StructureVersionResponse.from(service.submitForApproval(id));
    }

    @PostMapping("/versions/{id}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public StructureVersionResponse approve(@PathVariable UUID id,
                                            @RequestBody(required = false) StatusTransitionRequest req) {
        return StructureVersionResponse.from(
                service.approve(id, req == null ? null : req.reason()));
    }

    @PostMapping("/versions/{id}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public StructureVersionResponse reject(@PathVariable UUID id,
                                            @RequestBody(required = false) StatusTransitionRequest req) {
        return StructureVersionResponse.from(
                service.reject(id, req == null ? null : req.reason()));
    }

    @PostMapping("/versions/{id}/activate")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public StructureVersionResponse activate(@PathVariable UUID id) {
        return StructureVersionResponse.from(service.activate(id));
    }

    @PostMapping("/versions/rollback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public StructureVersionResponse rollback(@Valid @RequestBody RollbackRequest req) {
        return StructureVersionResponse.from(service.rollback(req));
    }

    // ---------- Units ----------

    @GetMapping("/versions/{id}/units")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR')")
    public List<OrgUnitResponse> listUnits(@PathVariable UUID id) {
        return service.unitsOf(id).stream().map(OrgUnitResponse::from).toList();
    }

    @GetMapping("/versions/{id}/tree")
    @PreAuthorize("isAuthenticated()")
    public OrgTreeNode tree(@PathVariable UUID id) {
        return service.buildTree(id);
    }

    @PostMapping("/versions/{id}/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public OrgUnitResponse addUnit(@PathVariable UUID id, @Valid @RequestBody OrgUnitRequest req) {
        return OrgUnitResponse.from(service.addUnit(id, req));
    }

    @PutMapping("/units/{unitId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public OrgUnitResponse updateUnit(@PathVariable UUID unitId,
                                       @Valid @RequestBody OrgUnitRequest req) {
        return OrgUnitResponse.from(service.updateUnit(unitId, req));
    }

    @DeleteMapping("/units/{unitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public void removeUnit(@PathVariable UUID unitId) {
        service.removeUnit(unitId);
    }
}
