package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.AssignmentResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.StartAssignmentRequest;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateRequest;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.service.ChecklistService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Onboarding / offboarding checklist endpoints (M105/M106).
 * Templates: HR_ADMIN_ONLY write, HR read.
 * Assignments: HR_WRITE to start/cancel, anyone authenticated can update
 *   task status (so IT / Manager / Finance / Employee owners can tick off).
 */
@RestController
@RequestMapping("/api/checklists")
public class ChecklistController {

    private static final String READ  = SecurityRoles.READ_HR;
    private static final String HR_WRITE = SecurityRoles.WRITE_HR;
    private static final String ADMIN_WRITE = SecurityRoles.WRITE_HR_ADMIN_ONLY;
    private static final String TASK_WRITE = "isAuthenticated()";

    private final ChecklistService service;

    public ChecklistController(ChecklistService service) {
        this.service = service;
    }

    // ── Templates ───────────────────────────────────────────────────────────

    @PostMapping("/templates")
    @PreAuthorize(ADMIN_WRITE)
    @ResponseStatus(HttpStatus.OK)
    public TemplateResponse upsertTemplate(@RequestBody TemplateRequest req) {
        return service.upsertTemplate(req);
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize(READ)
    public TemplateResponse getTemplate(@PathVariable UUID id) {
        return service.getTemplate(id);
    }

    @GetMapping("/templates")
    @PreAuthorize(READ)
    public List<TemplateResponse> templatesByFlow(@RequestParam ChecklistFlowType flow) {
        return service.templatesByFlow(flow);
    }

    // ── Assignments ─────────────────────────────────────────────────────────

    @PostMapping("/assignments")
    @PreAuthorize(HR_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse start(@RequestBody StartAssignmentRequest req) {
        return service.start(req);
    }

    @PutMapping("/task-statuses/{taskStatusId}")
    @PreAuthorize(TASK_WRITE)
    public AssignmentResponse updateTask(@PathVariable UUID taskStatusId,
                                          @RequestBody UpdateTaskRequest req) {
        return service.updateTask(taskStatusId, req);
    }

    @DeleteMapping("/assignments/{id}")
    @PreAuthorize(HR_WRITE)
    public AssignmentResponse cancel(@PathVariable UUID id,
                                      @RequestParam(required = false) String reason) {
        return service.cancel(id, reason);
    }

    @GetMapping("/assignments/{id}")
    @PreAuthorize(READ)
    public AssignmentResponse get(@PathVariable UUID id) {
        return service.getAssignment(id);
    }

    @GetMapping("/assignments/employees/{employeeId}")
    @PreAuthorize(READ)
    public List<AssignmentResponse> forEmployee(@PathVariable UUID employeeId) {
        return service.forEmployee(employeeId);
    }

    @GetMapping("/assignments/active")
    @PreAuthorize(READ)
    public List<AssignmentResponse> active(@RequestParam ChecklistFlowType flow) {
        return service.activeByFlow(flow);
    }
}
