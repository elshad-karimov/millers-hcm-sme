package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import az.millers.hcm.payroll.api.dto.ComponentAssignmentRequest;
import az.millers.hcm.payroll.api.dto.ComponentAssignmentResponse;
import az.millers.hcm.payroll.api.dto.SalaryComponentRequest;
import az.millers.hcm.payroll.api.dto.SalaryComponentResponse;
import az.millers.hcm.payroll.domain.ComponentKind;
import az.millers.hcm.payroll.service.SalaryComponentService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M349 — Salary component catalog + per-employee assignment management.
 */
@RestController
@RequestMapping("/api/payroll")
public class SalaryComponentController {

    private final SalaryComponentService service;

    public SalaryComponentController(SalaryComponentService service) {
        this.service = service;
    }

    @GetMapping("/components")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public Page<SalaryComponentResponse> list(
            @RequestParam(required = false) ComponentKind kind,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean isStatutory,
            Pageable pageable) {
        return service.list(kind, isActive, isStatutory, pageable)
                .map(SalaryComponentResponse::from);
    }

    @PostMapping("/components")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public SalaryComponentResponse create(@Valid @RequestBody SalaryComponentRequest req) {
        return SalaryComponentResponse.from(service.create(req));
    }

    @PutMapping("/components/{id}")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public SalaryComponentResponse update(@PathVariable UUID id, @Valid @RequestBody SalaryComponentRequest req) {
        return SalaryComponentResponse.from(service.update(id, req));
    }

    @DeleteMapping("/components/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    // ── Per-employee assignments ────────────────────────────────────────────

    @GetMapping("/employees/{employeeId}/component-assignments")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<ComponentAssignmentResponse> listAssignments(
            @PathVariable UUID employeeId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return service.listAssignments(employeeId, activeOnly).stream()
                .map(ComponentAssignmentResponse::from)
                .toList();
    }

    @PostMapping("/employees/{employeeId}/component-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public ComponentAssignmentResponse createAssignment(
            @PathVariable UUID employeeId,
            @Valid @RequestBody ComponentAssignmentRequest req) {
        return ComponentAssignmentResponse.from(service.createAssignment(employeeId, req));
    }

    @DeleteMapping("/employees/{employeeId}/component-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public void closeAssignment(@PathVariable UUID employeeId, @PathVariable UUID assignmentId) {
        service.closeAssignment(employeeId, assignmentId);
    }
}
