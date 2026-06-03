package az.millers.hcm.performance.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.api.dto.SuccessionGridDtos.BenchReport;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.DevelopmentList;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.PotentialRatingRequest;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.SuccessionGrid;
import az.millers.hcm.performance.service.SuccessionPlanService;
import az.millers.hcm.security.SecurityRoles;

/**
 * 9-box succession matrix endpoints (M92). HR-admin-and-above; explicitly
 * NOT exposed to department managers — succession decisions are made by
 * HR + Exec leadership in calibration sessions, not at the line-manager
 * level.
 */
@RestController
@RequestMapping("/api/performance/succession")
public class SuccessionPlanController {

    /** Read sees the grid; write updates a single employee's potential rating. */
    private static final String READ = SecurityRoles.READ_HR;
    private static final String WRITE = SecurityRoles.WRITE_HR_ADMIN_ONLY;

    private final SuccessionPlanService service;

    public SuccessionPlanController(SuccessionPlanService service) {
        this.service = service;
    }

    @GetMapping("/grid/{cycleId}")
    @PreAuthorize(READ)
    public SuccessionGrid grid(@PathVariable UUID cycleId) {
        return service.grid(cycleId);
    }

    /** M94 — per-manager bench depth (ready-now / ready-soon / long-term / dev). */
    @GetMapping("/bench/{cycleId}")
    @PreAuthorize(READ)
    public BenchReport bench(@PathVariable UUID cycleId) {
        return service.benchDepth(cycleId);
    }

    /**
     * M96 — drill from the {@code UNDER_DEVELOPMENT} bench cell to the
     * employees, with their currently active learning-path assignments
     * inlined so the UI can offer one-click "assign a path".
     *
     * @param managerId optional — narrow to one manager's direct reports.
     */
    @GetMapping("/development/{cycleId}")
    @PreAuthorize(READ)
    public DevelopmentList development(@PathVariable UUID cycleId,
                                        @RequestParam(required = false) UUID managerId) {
        return service.developmentBucket(cycleId, managerId);
    }

    @PutMapping("/reviews/{reviewId}/potential")
    @PreAuthorize(WRITE)
    public void setPotential(@PathVariable UUID reviewId,
                              @Valid @RequestBody PotentialRatingRequest req) {
        service.setPotential(reviewId, req);
    }
}
