package az.millers.hcm.career.api;

import az.millers.hcm.career.api.dto.*;
import az.millers.hcm.career.service.IdpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IDP / Career-path endpoints — M57.
 *
 * <h2>Access</h2>
 * <ul>
 *   <li>Employees can create and manage their own IDP via {@code /api/self/idp}</li>
 *   <li>HR can view/manage any employee's IDP via {@code /api/employees/{id}/idp}</li>
 *   <li>Manager comment endpoint requires HR_ADMIN or DEPARTMENT_MANAGER</li>
 * </ul>
 */
@RestController
public class IdpController {

    private final IdpService service;

    public IdpController(IdpService service) {
        this.service = service;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HR / admin access — any employee
    // ──────────────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @PostMapping("/api/employees/{employeeId}/idp")
    @ResponseStatus(HttpStatus.CREATED)
    public IdpResponse createForEmployee(@PathVariable UUID employeeId,
                                         @RequestBody IdpRequest req) {
        return service.create(employeeId, req);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN','DEPARTMENT_MANAGER')")
    @GetMapping("/api/employees/{employeeId}/idp")
    public List<IdpResponse> listForEmployee(@PathVariable UUID employeeId) {
        return service.list(employeeId);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN','DEPARTMENT_MANAGER')")
    @GetMapping("/api/idp/{idpId}")
    public IdpResponse get(@PathVariable UUID idpId) {
        return service.get(idpId);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @PostMapping("/api/idp/{idpId}/activate")
    public IdpResponse activate(@PathVariable UUID idpId) {
        return service.activate(idpId);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @PostMapping("/api/idp/{idpId}/complete")
    public IdpResponse complete(@PathVariable UUID idpId) {
        return service.complete(idpId);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @PostMapping("/api/idp/{idpId}/cancel")
    public IdpResponse cancel(@PathVariable UUID idpId) {
        return service.cancel(idpId);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','DEPARTMENT_MANAGER','SYSTEM_ADMIN')")
    @PostMapping("/api/idp/{idpId}/manager-comment")
    public IdpResponse managerComment(@PathVariable UUID idpId,
                                      @RequestBody Map<String, String> body) {
        return service.addManagerComment(idpId, body.get("comment"));
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @PostMapping("/api/idp/{idpId}/skill-gaps")
    @ResponseStatus(HttpStatus.CREATED)
    public IdpResponse addSkillGap(@PathVariable UUID idpId,
                                   @RequestBody SkillGapRequest req) {
        return service.addSkillGap(idpId, req);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @DeleteMapping("/api/idp/{idpId}/skill-gaps/{gapId}")
    public IdpResponse removeSkillGap(@PathVariable UUID idpId,
                                      @PathVariable UUID gapId) {
        return service.removeSkillGap(idpId, gapId);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @PostMapping("/api/idp/{idpId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public IdpResponse addActivity(@PathVariable UUID idpId,
                                   @RequestBody ActivityRequest req) {
        return service.addActivity(idpId, req);
    }

    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    @PatchMapping("/api/idp/{idpId}/activities/{activityId}/status")
    public IdpResponse updateActivityStatus(@PathVariable UUID idpId,
                                            @PathVariable UUID activityId,
                                            @RequestBody Map<String, String> body) {
        return service.updateActivityStatus(idpId, activityId, body.get("status"));
    }
}
