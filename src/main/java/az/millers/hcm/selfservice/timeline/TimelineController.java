package az.millers.hcm.selfservice.timeline;

import az.millers.hcm.security.SecurityRoles;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the unified employee timeline (M76 / P2-27/28). The
 * service is the only consumer of multiple repositories; this controller is
 * the thinnest possible adapter. Scope-checking lives in the service
 * (delegated to {@code AccessScopeService}) so the controller doesn't have
 * to special-case roles.
 */
@RestController
@RequestMapping("/api/employees/{employeeId}/timeline")
public class TimelineController {

    private final EmployeeTimelineService service;

    public TimelineController(EmployeeTimelineService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<TimelineEvent> get(@PathVariable UUID employeeId,
                                    @RequestParam(defaultValue = "200") int limit) {
        return service.forEmployee(employeeId, Math.min(Math.max(limit, 1), 500));
    }
}
