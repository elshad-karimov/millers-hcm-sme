package az.millers.hcm.workflow.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.workflow.api.dto.SlaBreachResponse;
import az.millers.hcm.workflow.service.WorkflowSlaService;

/**
 * M126 — read surface for the SLA breach dashboard + per-instance breach
 * history. The scheduler runs autonomously; this controller only exposes
 * the log the SPA needs to render.
 */
@RestController
@RequestMapping("/api/workflow/sla")
public class WorkflowSlaController {

    private final WorkflowSlaService service;

    public WorkflowSlaController(WorkflowSlaService service) {
        this.service = service;
    }

    @GetMapping("/breaches")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<SlaBreachResponse> recentBreaches(
            @RequestParam(defaultValue = "100") int limit) {
        return service.listRecentBreaches(limit);
    }

    @GetMapping("/instances/{instanceId}/breaches")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<SlaBreachResponse> historyForInstance(@PathVariable UUID instanceId) {
        return service.historyFor(instanceId);
    }
}
