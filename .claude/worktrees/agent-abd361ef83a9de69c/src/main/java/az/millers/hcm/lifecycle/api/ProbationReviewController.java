package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.CompleteProbationReviewRequest;
import az.millers.hcm.lifecycle.api.dto.ProbationReviewResponse;
import az.millers.hcm.lifecycle.api.dto.ScheduleProbationReviewRequest;
import az.millers.hcm.lifecycle.service.ProbationReviewService;

/**
 * REST surface for {@link az.millers.hcm.lifecycle.domain.ProbationReview}
 * (M73 / P2-01).
 *
 * <p>Two URL prefixes:
 * <ul>
 *   <li>{@code /api/employees/{id}/probation-reviews} — list per employee
 *       (read-mostly, surfaced on the M70 profile tabs).</li>
 *   <li>{@code /api/probation-reviews/...} — scheduling + completion ops
 *       on a specific review.</li>
 * </ul>
 *
 * <p>Roles: HR_ADMIN / HR_SPECIALIST / DEPT_MANAGER (scoped) / SYSTEM_ADMIN
 * / AUDITOR read; HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN write. Reviewer
 * employees (the line manager) can record their portion via the dedicated
 * complete endpoint when called from their scope.
 */
@RestController
public class ProbationReviewController {

    /** Centralised role sets — see {@link az.millers.hcm.security.SecurityRoles}. */
    private static final String READ_ROLES = az.millers.hcm.security.SecurityRoles.READ_HR_PLUS_MANAGERS;
    private static final String WRITE_ROLES = az.millers.hcm.security.SecurityRoles.WRITE_HR_PLUS_MANAGERS;

    private final ProbationReviewService service;

    public ProbationReviewController(ProbationReviewService service) {
        this.service = service;
    }

    @GetMapping("/api/employees/{employeeId}/probation-reviews")
    @PreAuthorize(READ_ROLES)
    public List<ProbationReviewResponse> listForEmployee(@PathVariable UUID employeeId) {
        return service.listForEmployee(employeeId);
    }

    @GetMapping("/api/probation-reviews/{id}")
    @PreAuthorize(READ_ROLES)
    public ProbationReviewResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/api/probation-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public ProbationReviewResponse schedule(@RequestBody @Valid ScheduleProbationReviewRequest req) {
        return service.schedule(req);
    }

    @PostMapping("/api/probation-reviews/{id}/complete")
    @PreAuthorize(WRITE_ROLES)
    public ProbationReviewResponse complete(@PathVariable UUID id,
                                             @RequestBody @Valid CompleteProbationReviewRequest req) {
        return service.complete(id, req);
    }
}
