package az.millers.hcm.learning.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.AssignRequest;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.AssignmentResponse;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.CancelRequest;
import az.millers.hcm.learning.api.dto.PathAssignmentDtos.SuggestedPath;
import az.millers.hcm.learning.service.LearningPathAssignmentService;
import az.millers.hcm.security.SecurityRoles;

/**
 * REST surface for {@link az.millers.hcm.learning.domain.LearningPathAssignment} (M95).
 *
 * <p>HR writes (assign / cancel); reads available to HR and the assignee
 * via {@code /me} for self-service "what's on my development plan?".
 */
@RestController
@RequestMapping("/api/learning/path-assignments")
public class LearningPathAssignmentController {

    private static final String READ = SecurityRoles.READ_HR;
    private static final String WRITE = SecurityRoles.WRITE_HR;

    private final LearningPathAssignmentService service;
    private final EmployeeContextService employeeContext;

    public LearningPathAssignmentController(LearningPathAssignmentService service,
                                             EmployeeContextService employeeContext) {
        this.service = service;
        this.employeeContext = employeeContext;
    }

    @PostMapping("/paths/{pathId}/assign")
    @PreAuthorize(WRITE)
    public AssignmentResponse assign(@PathVariable UUID pathId,
                                      @Valid @RequestBody AssignRequest req) {
        return service.assign(pathId, req);
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize(WRITE)
    public AssignmentResponse cancel(@PathVariable UUID assignmentId,
                                      @RequestBody(required = false) CancelRequest req) {
        return service.cancel(assignmentId, req);
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize(READ)
    public AssignmentResponse get(@PathVariable UUID assignmentId) {
        return service.get(assignmentId);
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize(READ)
    public List<AssignmentResponse> forEmployee(@PathVariable UUID employeeId) {
        return service.forEmployee(employeeId);
    }

    @GetMapping("/paths/{pathId}")
    @PreAuthorize(READ)
    public List<AssignmentResponse> forPath(@PathVariable UUID pathId) {
        return service.forPath(pathId);
    }

    /**
     * M98 — ranked path suggestions for an employee based on their
     * competency gaps. Active paths only; ones the employee already has
     * an active assignment for are still returned but sorted to the
     * bottom and flagged with {@code alreadyAssigned}.
     */
    @GetMapping("/suggestions/{employeeId}")
    @PreAuthorize(READ)
    public List<SuggestedPath> suggestions(@PathVariable UUID employeeId) {
        return service.suggestForEmployee(employeeId);
    }

    /** Self-service: the caller's own assignments. Any authenticated user. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<AssignmentResponse> mine() {
        Employee emp = employeeContext.currentEmployee();
        return emp == null ? List.of() : service.forEmployee(emp.getId());
    }
}
