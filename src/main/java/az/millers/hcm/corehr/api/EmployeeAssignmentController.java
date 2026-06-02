package az.millers.hcm.corehr.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
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

import az.millers.hcm.corehr.api.dto.EmployeeAssignmentRequest;
import az.millers.hcm.corehr.api.dto.EmployeeAssignmentResponse;
import az.millers.hcm.corehr.service.EmployeeAssignmentService;
import jakarta.validation.Valid;

/**
 * REST surface for the M75 {@code EmployeeAssignment} table — full assignment
 * history nested under an employee. Mirrors the {@code /api/employees/{id}/...}
 * pattern used by other Phase 2 sub-entities (M71 dependents/education,
 * M72 assets/notes/rewards, M74 bank-accounts).
 */
@RestController
@RequestMapping("/api/employees/{employeeId}/assignments")
public class EmployeeAssignmentController {

    private static final String READ_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN','AUDITOR')";
    private static final String WRITE_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')";

    private final EmployeeAssignmentService service;

    public EmployeeAssignmentController(EmployeeAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<EmployeeAssignmentResponse> list(
            @PathVariable UUID employeeId,
            @RequestParam(defaultValue = "false") boolean openOnly) {
        return openOnly ? service.openFor(employeeId) : service.listFor(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public EmployeeAssignmentResponse create(@PathVariable UUID employeeId,
                                              @Valid @RequestBody EmployeeAssignmentRequest req) {
        if (!employeeId.equals(req.employeeId())) {
            throw new az.millers.hcm.common.BadRequestException(
                    "Path employeeId does not match body employeeId");
        }
        return service.create(req);
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize(WRITE_ROLES)
    public EmployeeAssignmentResponse update(@PathVariable UUID employeeId,
                                              @PathVariable UUID assignmentId,
                                              @Valid @RequestBody EmployeeAssignmentRequest req) {
        if (!employeeId.equals(req.employeeId())) {
            throw new az.millers.hcm.common.BadRequestException(
                    "Path employeeId does not match body employeeId");
        }
        return service.update(assignmentId, req);
    }

    @PostMapping("/{assignmentId}/close")
    @PreAuthorize(WRITE_ROLES)
    public EmployeeAssignmentResponse close(@PathVariable UUID employeeId,
                                             @PathVariable UUID assignmentId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                             LocalDate closeOn) {
        return service.close(assignmentId, closeOn);
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void delete(@PathVariable UUID employeeId, @PathVariable UUID assignmentId) {
        service.delete(assignmentId);
    }
}
