package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

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

import az.millers.hcm.compensation.api.dto.CompensationBudgetResponse;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.CloneRequest;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.DiffResponse;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.LineRequest;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.LineResponse;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.PlanHeaderRequest;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.PlanResponse;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.RejectRequest;
import az.millers.hcm.staffing.api.dto.WorkforcePlanDtos.VarianceResponse;
import az.millers.hcm.staffing.service.WorkforcePlanService;
import jakarta.validation.Valid;

/** M247 — REST surface for workforce plans + scenarios. */
@RestController
@RequestMapping("/api/workforce-plans")
public class WorkforcePlanController {

    private final WorkforcePlanService service;

    public WorkforcePlanController(WorkforcePlanService service) {
        this.service = service;
    }

    // ── Headers ─────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public List<PlanResponse> list(@RequestParam(required = false) UUID legalEntityId) {
        var rows = legalEntityId == null ? service.listAll() : service.listForLegalEntity(legalEntityId);
        return rows.stream()
                .map(p -> PlanResponse.from(p, service.linesFor(p.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public PlanResponse get(@PathVariable UUID id) {
        var p = service.get(id);
        return PlanResponse.from(p, service.linesFor(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public PlanResponse create(@Valid @RequestBody PlanHeaderRequest req) {
        return PlanResponse.from(service.create(req.toEntity()), List.of());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public PlanResponse update(@PathVariable UUID id, @Valid @RequestBody PlanHeaderRequest req) {
        return PlanResponse.from(service.update(id, req.toEntity()), service.linesFor(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ── Clone as scenario ───────────────────────────────────────────

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public PlanResponse clone(@PathVariable UUID id, @Valid @RequestBody CloneRequest req) {
        var clone = service.cloneAsScenario(id, req.newVersionCode(), req.newTitle(), req.scenarioType());
        return PlanResponse.from(clone, service.linesFor(clone.getId()));
    }

    // ── Lines ───────────────────────────────────────────────────────

    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public List<LineResponse> lines(@PathVariable UUID id) {
        return service.linesFor(id).stream().map(LineResponse::from).toList();
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public LineResponse addLine(@PathVariable UUID id, @Valid @RequestBody LineRequest req) {
        return LineResponse.from(service.addLine(id, req.toEntity()));
    }

    @PutMapping("/lines/{lineId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public LineResponse updateLine(@PathVariable UUID lineId, @Valid @RequestBody LineRequest req) {
        return LineResponse.from(service.updateLine(lineId, req.toEntity()));
    }

    @DeleteMapping("/lines/{lineId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public void deleteLine(@PathVariable UUID lineId) {
        service.deleteLine(lineId);
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public PlanResponse submit(@PathVariable UUID id) {
        return PlanResponse.from(service.submit(id), service.linesFor(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public PlanResponse approve(@PathVariable UUID id) {
        return PlanResponse.from(service.approve(id), service.linesFor(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public PlanResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return PlanResponse.from(service.reject(id, req.reason()), service.linesFor(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PlanResponse archive(@PathVariable UUID id) {
        return PlanResponse.from(service.archive(id), service.linesFor(id));
    }

    // ── Compare / Variance ──────────────────────────────────────────

    @GetMapping("/{idA}/compare/{idB}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public DiffResponse compare(@PathVariable UUID idA, @PathVariable UUID idB) {
        return DiffResponse.from(service.comparePlans(idA, idB));
    }

    @GetMapping("/{id}/variance-vs-actual")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public VarianceResponse variance(@PathVariable UUID id) {
        return VarianceResponse.from(service.varianceVsActual(id));
    }

    // ── M424: Transfer to Budget ───────────────────────────────────────

    @PostMapping("/{id}/transfer-to-budget")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public CompensationBudgetResponse transferToBudget(@PathVariable UUID id) {
        return CompensationBudgetResponse.from(service.transferToBudget(id));
    }
}
