package az.millers.hcm.compbenefits.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.CycleRequest;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.CycleResponse;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.DecisionRequest;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.ProposalRequest;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.ProposalResponse;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.TeamGrid;
import az.millers.hcm.compbenefits.domain.CompCycleStatus;
import az.millers.hcm.compbenefits.service.CompPlanningService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Compensation planning workbench REST (M118 Phase 1).
 *
 * <ul>
 *   <li>Cycle CRUD — HR_ADMIN_ONLY (pool numbers feed payroll).</li>
 *   <li>Reads (cycle list, team grid, proposals) — HR_PLUS_MANAGERS so a
 *       manager sees their team and HR sees everyone.</li>
 *   <li>Proposal write — HR_PLUS_MANAGERS (the proposing manager is the
 *       common case; HR-admin override stays gated on the same rule).</li>
 *   <li>Decision (approve / reject) — HR_ADMIN_ONLY. Approval also writes
 *       through to {@code EmployeeCompensation}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/compbenefits/comp-planning")
public class CompPlanningController {

    private final CompPlanningService service;
    /** M129 — bonus payout simulator. */
    private final az.millers.hcm.compbenefits.service.BonusSimulationService simulator;

    public CompPlanningController(CompPlanningService service,
                                  az.millers.hcm.compbenefits.service.BonusSimulationService simulator) {
        this.service = service;
        this.simulator = simulator;
    }

    // ── M129 — Simulator ──────────────────────────────────────────────
    @org.springframework.web.bind.annotation.PostMapping("/cycles/{id}/simulate")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public az.millers.hcm.compbenefits.api.dto.SimulationDtos.SimulationResponse simulate(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
                    az.millers.hcm.compbenefits.api.dto.SimulationDtos.SimulationRequest req) {
        return simulator.simulate(id, req);
    }

    // ── Cycles ──────────────────────────────────────────────────────────

    @GetMapping("/cycles")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<CycleResponse> listCycles(@RequestParam(required = false) CompCycleStatus status) {
        return service.listCycles(status);
    }

    @GetMapping("/cycles/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public CycleResponse getCycle(@PathVariable UUID id) {
        return service.getCycle(id);
    }

    @PostMapping("/cycles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CycleResponse createCycle(@Valid @RequestBody CycleRequest req) {
        return service.createCycle(req);
    }

    @PutMapping("/cycles/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CycleResponse updateCycle(@PathVariable UUID id, @Valid @RequestBody CycleRequest req) {
        return service.updateCycle(id, req);
    }

    public record StatusChangeRequest(CompCycleStatus status) {}

    @PostMapping("/cycles/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CycleResponse changeStatus(@PathVariable UUID id,
                                       @RequestBody StatusChangeRequest req) {
        return service.changeCycleStatus(id, req.status());
    }

    // ── Team grid ───────────────────────────────────────────────────────

    @GetMapping("/cycles/{id}/team-grid")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public TeamGrid teamGrid(@PathVariable("id") UUID cycleId,
                              @RequestParam List<UUID> employeeIds) {
        return service.teamGridFor(cycleId, employeeIds);
    }

    @GetMapping("/cycles/{id}/proposals")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<ProposalResponse> proposalsFor(@PathVariable("id") UUID cycleId) {
        return service.proposalsForCycle(cycleId);
    }

    // ── Proposals ───────────────────────────────────────────────────────

    @PostMapping("/proposals")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public ProposalResponse upsertProposal(@Valid @RequestBody ProposalRequest req) {
        return service.upsertProposal(req);
    }

    @PostMapping("/proposals/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public ProposalResponse submitProposal(@PathVariable UUID id) {
        return service.submitProposal(id);
    }

    @PostMapping("/proposals/{id}/decide")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ProposalResponse decideProposal(@PathVariable UUID id,
                                            @Valid @RequestBody DecisionRequest req) {
        return service.decideProposal(id, req);
    }
}
