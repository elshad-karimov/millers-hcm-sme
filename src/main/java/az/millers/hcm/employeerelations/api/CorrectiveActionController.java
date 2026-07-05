package az.millers.hcm.employeerelations.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.employeerelations.domain.CorrectiveActionPlan;
import az.millers.hcm.employeerelations.domain.CorrectiveActionStatus;
import az.millers.hcm.employeerelations.service.CorrectiveActionService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M447 — Corrective action plan controller.
 */
@RestController
@RequestMapping("/api/er/corrective-actions")
public class CorrectiveActionController {

    private final CorrectiveActionService service;

    public CorrectiveActionController(CorrectiveActionService service) {
        this.service = service;
    }

    public record CreatePlanRequest(
            UUID erCaseId,
            UUID disciplinaryActionId,
            UUID employeeId,
            String actionRequired,
            String responsibleUsername,
            LocalDate dueDate,
            LocalDate followUpDate) {}

    public record UpdateStatusRequest(
            CorrectiveActionStatus status,
            String notes) {}

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CorrectiveActionPlan create(@RequestBody CreatePlanRequest req) {
        return service.create(req.erCaseId(), req.disciplinaryActionId(),
                req.employeeId(), req.actionRequired(), req.responsibleUsername(),
                req.dueDate(), req.followUpDate());
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<CorrectiveActionPlan> list(
            @RequestParam(required = false) CorrectiveActionStatus status,
            @RequestParam(required = false) String responsible) {
        return service.list(status, responsible);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public CorrectiveActionPlan get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CorrectiveActionPlan updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(id, req.status(), req.notes());
    }
}
