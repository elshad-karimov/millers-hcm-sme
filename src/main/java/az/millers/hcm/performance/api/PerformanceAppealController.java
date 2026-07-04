package az.millers.hcm.performance.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.api.dto.PerformanceReviewResponse;
import az.millers.hcm.performance.domain.PerformanceAppeal;
import az.millers.hcm.performance.service.PerformanceAppealService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Acknowledgement (§25) + appeals (§26) API — HCM_12 M396. */
@RestController
@RequestMapping("/api/performance")
public class PerformanceAppealController {

    private final PerformanceAppealService service;

    public PerformanceAppealController(PerformanceAppealService service) {
        this.service = service;
    }

    public record AcknowledgeRequest(@Size(max = 2000) String comments, boolean disputed) {}

    public record AppealSubmitRequest(@NotBlank @Size(max = 2000) String reason) {}

    public record AppealDecisionRequest(@NotBlank String decision, BigDecimal adjustedRating,
                                        @Size(max = 2000) String notes) {}

    public record AppealResponse(
            UUID id, UUID reviewId, UUID employeeId, String reason, String status,
            BigDecimal originalRating, BigDecimal adjustedRating, String decisionNotes,
            String submittedBy, OffsetDateTime submittedAt, String decidedBy,
            OffsetDateTime decidedAt, OffsetDateTime closedAt) {

        static AppealResponse from(PerformanceAppeal a) {
            return new AppealResponse(a.getId(), a.getReviewId(), a.getEmployeeId(), a.getReason(),
                    a.getStatus(), a.getOriginalRating(), a.getAdjustedRating(), a.getDecisionNotes(),
                    a.getSubmittedBy(), a.getSubmittedAt(), a.getDecidedBy(), a.getDecidedAt(),
                    a.getClosedAt());
        }
    }

    // ── §25 acknowledgement ──────────────────────────────────────────────────

    @PostMapping("/reviews/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public PerformanceReviewResponse acknowledge(@PathVariable UUID id,
                                                 @RequestBody AcknowledgeRequest req) {
        return PerformanceReviewResponse.from(
                service.acknowledge(id, req.comments(), req.disputed()));
    }

    // ── §26 appeals ──────────────────────────────────────────────────────────

    @GetMapping("/appeals")
    @PreAuthorize("isAuthenticated()")
    public List<AppealResponse> list(@RequestParam(required = false) UUID reviewId,
                                     @RequestParam(required = false) String status) {
        return service.list(reviewId, status).stream().map(AppealResponse::from).toList();
    }

    @PostMapping("/reviews/{id}/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AppealResponse submit(@PathVariable UUID id, @RequestBody AppealSubmitRequest req) {
        return AppealResponse.from(service.submit(id, req.reason()));
    }

    @PostMapping("/appeals/{id}/under-review")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AppealResponse takeUnderReview(@PathVariable UUID id) {
        return AppealResponse.from(service.takeUnderReview(id));
    }

    /** APPROVED (optional adjustedRating via M394 override) | REJECTED | RETURNED. */
    @PostMapping("/appeals/{id}/decide")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AppealResponse decide(@PathVariable UUID id, @RequestBody AppealDecisionRequest req) {
        return AppealResponse.from(service.decide(id, req.decision(), req.adjustedRating(), req.notes()));
    }

    @PostMapping("/appeals/{id}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public AppealResponse resubmit(@PathVariable UUID id,
                                   @RequestParam(required = false) String extraInfo) {
        return AppealResponse.from(service.resubmit(id, extraInfo));
    }

    @PostMapping("/appeals/{id}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AppealResponse close(@PathVariable UUID id) {
        return AppealResponse.from(service.close(id));
    }
}
