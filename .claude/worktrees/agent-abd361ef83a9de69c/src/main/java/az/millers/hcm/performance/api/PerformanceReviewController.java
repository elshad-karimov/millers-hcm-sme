package az.millers.hcm.performance.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.performance.api.dto.CalibrationRequest;
import az.millers.hcm.performance.api.dto.ManagerReviewRequest;
import az.millers.hcm.performance.api.dto.PerformanceReviewResponse;
import az.millers.hcm.performance.api.dto.ReviewStartRequest;
import az.millers.hcm.performance.api.dto.SelfReviewRequest;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewStatus;
import az.millers.hcm.performance.service.PerformanceReviewService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/performance/reviews")
public class PerformanceReviewController {

    private final PerformanceReviewService service;

    public PerformanceReviewController(PerformanceReviewService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<PerformanceReviewResponse> list(
            @RequestParam(required = false) UUID cycleId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PerformanceReview> result = service.list(cycleId, employeeId, status, PageRequest.of(page, size));
        return PageResponse.of(result, PerformanceReviewResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PerformanceReviewResponse get(@PathVariable UUID id) {
        return PerformanceReviewResponse.from(service.get(id));
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PerformanceReviewResponse start(@Valid @RequestBody ReviewStartRequest req) {
        return PerformanceReviewResponse.from(service.start(req));
    }

    @PostMapping("/{id}/self")
    @PreAuthorize("isAuthenticated()")
    public PerformanceReviewResponse submitSelf(@PathVariable UUID id,
                                                  @Valid @RequestBody SelfReviewRequest req) {
        return PerformanceReviewResponse.from(service.submitSelfReview(id, req));
    }

    @PostMapping("/{id}/manager")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PerformanceReviewResponse submitManager(@PathVariable UUID id,
                                                     @Valid @RequestBody ManagerReviewRequest req) {
        return PerformanceReviewResponse.from(service.submitManagerReview(id, req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PerformanceReviewResponse submitForApproval(@PathVariable UUID id) {
        return PerformanceReviewResponse.from(service.submitForApproval(id));
    }

    @PostMapping("/{id}/calibrate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PerformanceReviewResponse calibrate(@PathVariable UUID id,
                                                 @Valid @RequestBody CalibrationRequest req) {
        return PerformanceReviewResponse.from(service.calibrate(id, req));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PerformanceReviewResponse close(@PathVariable UUID id) {
        return PerformanceReviewResponse.from(service.close(id));
    }

    /** M218 — HR_ADMIN can cancel a review that is PENDING_APPROVAL. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PerformanceReviewResponse cancel(@PathVariable UUID id,
                                             @RequestParam(required = false) String reason) {
        return PerformanceReviewResponse.from(service.onCancelled(id, reason));
    }
}
