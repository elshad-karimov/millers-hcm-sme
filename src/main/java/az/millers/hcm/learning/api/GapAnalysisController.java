package az.millers.hcm.learning.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.learning.api.dto.GapItem;
import az.millers.hcm.learning.api.dto.PositionFitResponse;
import az.millers.hcm.learning.api.dto.PositionRequirementRequest;
import az.millers.hcm.learning.api.dto.PositionRequirementResponse;
import az.millers.hcm.learning.service.GapAnalysisService;
import az.millers.hcm.learning.service.PositionFitService;

/**
 * M60: REST surface for competency gap analysis.
 *
 * <ul>
 *   <li>Position requirement management — HR_ADMIN / SYSTEM_ADMIN only.</li>
 *   <li>Employee gap report — HR_ADMIN, HR_SPECIALIST, DEPARTMENT_MANAGER, SYSTEM_ADMIN.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/learning")
public class GapAnalysisController {

    private final GapAnalysisService service;
    private final PositionFitService positionFit;

    public GapAnalysisController(GapAnalysisService service, PositionFitService positionFit) {
        this.service = service;
        this.positionFit = positionFit;
    }

    // ── Position requirements ─────────────────────────────────────────────────

    @GetMapping("/positions/{positionId}/requirements")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN')")
    public List<PositionRequirementResponse> listRequirements(@PathVariable UUID positionId) {
        return service.requirementsForPosition(positionId);
    }

    @PostMapping("/positions/{positionId}/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionRequirementResponse addRequirement(@PathVariable UUID positionId,
                                                      @RequestBody @Valid PositionRequirementRequest req) {
        return service.addOrUpdateRequirement(positionId, req);
    }

    @DeleteMapping("/positions/{positionId}/requirements/{competencyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void removeRequirement(@PathVariable UUID positionId,
                                   @PathVariable UUID competencyId) {
        service.removeRequirement(positionId, competencyId);
    }

    // ── Gap analysis ──────────────────────────────────────────────────────────

    /**
     * Returns the competency gap report for an employee against their assigned
     * position's requirements.  Sorted by gap size descending — biggest deficits first.
     */
    @GetMapping("/employees/{employeeId}/gap-analysis")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN')")
    public List<GapItem> gapAnalysis(@PathVariable UUID employeeId) {
        return service.gapAnalysis(employeeId);
    }

    // ── Candidate-fit ranking ───────────────────────────────────────────────

    /**
     * Ranks active employees by how well their current competency levels meet
     * the position's requirements (best fit first). Mirrors the retired
     * {@code /api/skills/positions/{id}/fit}, now backed by the canonical
     * {@code learning.position_competency_requirement} table.
     */
    @GetMapping("/positions/{positionId}/fit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN')")
    public PositionFitResponse positionFit(@PathVariable UUID positionId) {
        return positionFit.rankCandidatesForPosition(positionId);
    }
}
