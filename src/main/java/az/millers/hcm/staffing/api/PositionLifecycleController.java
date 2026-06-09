package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionLifecycleDtos.LifecycleActionRequest;
import az.millers.hcm.staffing.api.dto.PositionLifecycleDtos.LifecycleEventResponse;
import az.millers.hcm.staffing.api.dto.PositionResponse;
import az.millers.hcm.staffing.service.PositionLifecycleService;
import jakarta.validation.Valid;

/**
 * M243 — REST surface for the position lifecycle state machine.
 *
 * <p>One endpoint per transition; all share the same
 * {@link LifecycleActionRequest} body so the SPA can use a single
 * helper. Authorisation tier:
 * <ul>
 *   <li>submit / unfreeze / finish-review — HR_TEAM_WRITE (HR_ADMIN +
 *       HR_SPECIALIST), since these are routine moves.</li>
 *   <li>approve / reject / activate / freeze / mark-under-review /
 *       close / archive — HR_ADMIN only, since they affect downstream
 *       recruitment & payroll.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/positions/{id}/lifecycle")
public class PositionLifecycleController {

    private final PositionLifecycleService lifecycle;

    public PositionLifecycleController(PositionLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    // ── Routine transitions (HR_ADMIN + HR_SPECIALIST) ────────────────────

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public PositionResponse submit(@PathVariable UUID id,
                                    @Valid @RequestBody(required = false) LifecycleActionRequest req) {
        return PositionResponse.from(
                lifecycle.submitForApproval(id, req == null ? null : req.comments()));
    }

    @PostMapping("/unfreeze")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public PositionResponse unfreeze(@PathVariable UUID id,
                                      @Valid @RequestBody(required = false) LifecycleActionRequest req) {
        return PositionResponse.from(
                lifecycle.unfreeze(id, req == null ? null : req.comments()));
    }

    @PostMapping("/finish-review")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public PositionResponse finishReview(@PathVariable UUID id,
                                          @Valid @RequestBody(required = false) LifecycleActionRequest req) {
        return PositionResponse.from(
                lifecycle.finishReview(id, req == null ? null : req.comments()));
    }

    // ── HR_ADMIN-only transitions ─────────────────────────────────────────

    @PostMapping("/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionResponse approve(@PathVariable UUID id,
                                     @Valid @RequestBody(required = false) LifecycleActionRequest req) {
        return PositionResponse.from(
                lifecycle.approve(id, req == null ? null : req.comments()));
    }

    @PostMapping("/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionResponse reject(@PathVariable UUID id,
                                    @Valid @RequestBody LifecycleActionRequest req) {
        return PositionResponse.from(lifecycle.reject(id, req.reason()));
    }

    @PostMapping("/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionResponse activate(@PathVariable UUID id) {
        return PositionResponse.from(lifecycle.activate(id));
    }

    @PostMapping("/freeze")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionResponse freeze(@PathVariable UUID id,
                                    @Valid @RequestBody LifecycleActionRequest req) {
        return PositionResponse.from(
                lifecycle.freeze(id, req.reason(), req.scheduledUnfreezeDate(), req.comments()));
    }

    @PostMapping("/under-review")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionResponse markUnderReview(@PathVariable UUID id,
                                              @Valid @RequestBody LifecycleActionRequest req) {
        return PositionResponse.from(lifecycle.markUnderReview(id, req.reason()));
    }

    @PostMapping("/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionResponse close(@PathVariable UUID id,
                                   @Valid @RequestBody LifecycleActionRequest req) {
        return PositionResponse.from(lifecycle.close(id, req.reason()));
    }

    @PostMapping("/archive")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PositionResponse archive(@PathVariable UUID id) {
        return PositionResponse.from(lifecycle.archive(id));
    }

    // ── History ───────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER')")
    public List<LifecycleEventResponse> history(@PathVariable UUID id) {
        return lifecycle.history(id).stream()
                .map(LifecycleEventResponse::from)
                .toList();
    }
}
