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

import az.millers.hcm.performance.domain.DevelopmentAction;
import az.millers.hcm.performance.domain.DevelopmentPlan;
import az.millers.hcm.performance.service.DevPlanService;
import az.millers.hcm.security.SecurityRoles;

/** Development-plan API (HCM_12 M399, PRD §21). Hierarchy-guarded in the service. */
@RestController
@RequestMapping("/api/performance/dev-plans")
public class DevPlanController {

    private final DevPlanService service;

    public DevPlanController(DevPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<DevelopmentPlan> list(@RequestParam(required = false) UUID employeeId,
                                      @RequestParam(required = false) String status) {
        return service.list(employeeId, status);
    }

    @GetMapping("/{id}/actions")
    @PreAuthorize("isAuthenticated()")
    public List<DevelopmentAction> actions(@PathVariable UUID id) {
        return service.actionsFor(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public DevelopmentPlan create(@RequestBody DevPlanService.PlanRequest req) {
        return service.save(null, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public DevelopmentPlan update(@PathVariable UUID id,
                                  @RequestBody DevPlanService.PlanRequest req) {
        return service.save(id, req);
    }

    /** §21.3 — build a plan from the review's positive competency gaps. */
    @PostMapping("/from-review/{reviewId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public DevelopmentPlan createFromGaps(@PathVariable UUID reviewId) {
        return service.createFromGaps(reviewId);
    }

    @PostMapping("/{id}/status/{status}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public DevelopmentPlan changeStatus(@PathVariable UUID id, @PathVariable String status) {
        return service.changeStatus(id, status);
    }

    /** Mark one action; plan progress rolls up (employees can tick their own). */
    @PostMapping("/actions/{actionId}/status/{status}")
    @PreAuthorize("isAuthenticated()")
    public DevelopmentPlan markAction(@PathVariable UUID actionId, @PathVariable String status) {
        return service.markAction(actionId, status);
    }
}
