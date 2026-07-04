package az.millers.hcm.performance.api;

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

import az.millers.hcm.performance.domain.TalentReview;
import az.millers.hcm.performance.domain.TalentReviewCycle;
import az.millers.hcm.performance.service.TalentReviewService;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_15 M411 — talent review cycles + panel decisions (PRD §15.7/§15.8).
 * HR-only: employees NEVER see their own HiPo/retention status (GLOBAL RULE 17).
 */
@RestController
@RequestMapping("/api/talent/reviews")
public class TalentReviewController {

    public record CycleRequest(String name, int year) {}

    public record ReviewRequest(UUID employeeId, Integer performanceBox, Integer potentialBox,
                                Boolean hipoDecision, String retentionRisk,
                                String retentionAction, String notes) {}

    private final TalentReviewService service;

    public TalentReviewController(TalentReviewService service) {
        this.service = service;
    }

    // ── Cycles ──────────────────────────────────────────────────────────────

    @GetMapping("/cycles")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<TalentReviewCycle> listCycles(@RequestParam(required = false) String status) {
        return service.listCycles(status);
    }

    @PostMapping("/cycles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TalentReviewCycle createCycle(@RequestBody CycleRequest req) {
        return service.createCycle(req.name(), req.year());
    }

    @PutMapping("/cycles/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TalentReviewCycle updateCycleStatus(@PathVariable UUID id,
                                               @RequestParam String status) {
        return service.updateCycleStatus(id, status);
    }

    // ── Reviews ─────────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleId}/reviews")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<TalentReview> listReviews(@PathVariable UUID cycleId) {
        return service.listReviews(cycleId);
    }

    @PostMapping("/cycles/{cycleId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TalentReview recordReview(@PathVariable UUID cycleId, @RequestBody ReviewRequest req) {
        return service.recordReview(cycleId, req.employeeId(), req.performanceBox(),
                req.potentialBox(), req.hipoDecision(), req.retentionRisk(),
                req.retentionAction(), req.notes());
    }
}
