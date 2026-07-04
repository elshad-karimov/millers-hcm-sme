package az.millers.hcm.skills.api;

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

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.skills.api.SkillsDtos.EmployeeGapResponse;
import az.millers.hcm.skills.api.SkillsDtos.PositionCompetencyRequest;
import az.millers.hcm.skills.api.SkillsDtos.PositionCompetencyResponse;
import az.millers.hcm.skills.api.SkillsDtos.PositionFitResponse;
import az.millers.hcm.skills.service.PositionRequirementsService;
import az.millers.hcm.skills.service.SkillGapService;

/**
 * M127 — REST surface for position requirements + skill-gap analysis.
 *
 * <p>CRUD on position requirements is HR-write only. Reading the gap
 * or the fit ranking is HR + managers — line managers can fairly check
 * where a direct report stands against a stretch position.
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final PositionRequirementsService requirements;
    private final SkillGapService gaps;

    public SkillsController(PositionRequirementsService requirements,
                            SkillGapService gaps) {
        this.requirements = requirements;
        this.gaps = gaps;
    }

    // ── Position requirements CRUD ──────────────────────────────────────────

    @GetMapping("/positions/{positionId}/requirements")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<PositionCompetencyResponse> requirementsFor(@PathVariable UUID positionId) {
        return requirements.listFor(positionId);
    }

    @PutMapping("/positions/{positionId}/requirements")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public PositionCompetencyResponse upsert(@PathVariable UUID positionId,
                                              @RequestBody PositionCompetencyRequest req) {
        return requirements.upsert(positionId, req);
    }

    @PostMapping("/positions/{positionId}/requirements")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public PositionCompetencyResponse create(@PathVariable UUID positionId,
                                              @RequestBody PositionCompetencyRequest req) {
        return requirements.upsert(positionId, req);
    }

    @DeleteMapping("/positions/{positionId}/requirements/{competencyId}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<Void> remove(@PathVariable UUID positionId,
                                        @PathVariable UUID competencyId) {
        requirements.remove(positionId, competencyId);
        return ResponseEntity.noContent().build();
    }

    // ── Gap analysis ───────────────────────────────────────────────────────

    @GetMapping("/employees/{employeeId}/gap/{positionId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public EmployeeGapResponse employeeGap(@PathVariable UUID employeeId,
                                            @PathVariable UUID positionId) {
        return gaps.gapForEmployee(employeeId, positionId);
    }

    @GetMapping("/positions/{positionId}/fit")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PositionFitResponse positionFit(@PathVariable UUID positionId) {
        return gaps.rankCandidatesForPosition(positionId);
    }
}
