package az.millers.hcm.performance.api;

import java.time.LocalDate;
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

import az.millers.hcm.performance.domain.PerformancePip;
import az.millers.hcm.performance.domain.PipCheckpoint;
import az.millers.hcm.performance.service.PipService;
import az.millers.hcm.security.SecurityRoles;

/** PIP API (HCM_12 M398, PRD §20). Hierarchy-guarded in the service. */
@RestController
@RequestMapping("/api/performance/pips")
public class PipController {

    private final PipService service;

    public PipController(PipService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<PerformancePip> list(@RequestParam(required = false) UUID employeeId,
                                     @RequestParam(required = false) String status) {
        return service.list(employeeId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PerformancePip get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PerformancePip create(@RequestBody PipService.PipRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PerformancePip update(@PathVariable UUID id, @RequestBody PipService.PipRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PerformancePip activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    /** Employee acknowledgement — self-service surface uses this too. */
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public PerformancePip acknowledge(@PathVariable UUID id,
                                      @RequestParam(required = false) String comments) {
        return service.acknowledge(id, comments);
    }

    @PostMapping("/{id}/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PipCheckpoint addCheckpoint(@PathVariable UUID id,
                                       @RequestBody PipService.CheckpointRequest req) {
        return service.addCheckpoint(id, req);
    }

    @GetMapping("/{id}/checkpoints")
    @PreAuthorize("isAuthenticated()")
    public List<PipCheckpoint> checkpoints(@PathVariable UUID id) {
        return service.checkpoints(id);
    }

    @PostMapping("/{id}/extend")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public PerformancePip extend(@PathVariable UUID id, @RequestParam LocalDate newEndDate) {
        return service.extend(id, newEndDate);
    }

    /** Outcome decisions are HR-controlled (§20.4). */
    @PostMapping("/{id}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PerformancePip close(@PathVariable UUID id,
                                @RequestParam String outcome,
                                @RequestParam(required = false) String notes) {
        return service.close(id, outcome, notes);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PerformancePip cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
