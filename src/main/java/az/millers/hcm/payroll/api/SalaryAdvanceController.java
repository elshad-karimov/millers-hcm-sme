package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.ApproveAdvanceRequest;
import az.millers.hcm.payroll.api.dto.RejectAdvanceRequest;
import az.millers.hcm.payroll.api.dto.SalaryAdvanceRequest;
import az.millers.hcm.payroll.api.dto.SalaryAdvanceResponse;
import az.millers.hcm.payroll.domain.SalaryAdvance;
import az.millers.hcm.payroll.service.SalaryAdvanceService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;

/**
 * M351 — Salary advance management.
 * Employees request advances via self-service; HR approves/rejects.
 */
@RestController
@RequestMapping("/api/payroll/advances")
public class SalaryAdvanceController {

    private final SalaryAdvanceService service;
    private final EmployeeContextService employeeContext;
    private final CurrentRequest currentRequest;

    public SalaryAdvanceController(SalaryAdvanceService service,
                                    EmployeeContextService employeeContext,
                                    CurrentRequest currentRequest) {
        this.service = service;
        this.employeeContext = employeeContext;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL + " or hasRole('" + SecurityRoles.R_EMPLOYEE + "')")
    public List<SalaryAdvanceResponse> list(@RequestParam(required = false) UUID employeeId) {
        // EMPLOYEE role: force employeeId from context, ignore query param
        if (currentRequest.hasRole(SecurityRoles.R_EMPLOYEE) && !currentRequest.hasRole(SecurityRoles.R_HR_ADMIN)) {
            UUID contextEmployeeId = employeeContext.currentEmployee().getId();
            return service.findByEmployeeId(contextEmployeeId).stream()
                    .map(SalaryAdvanceResponse::from)
                    .toList();
        }
        // HR: filter by employeeId if provided, else return all
        if (employeeId != null) {
            return service.findByEmployeeId(employeeId).stream()
                    .map(SalaryAdvanceResponse::from)
                    .toList();
        }
        return service.findAll().stream()
                .map(SalaryAdvanceResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL + " or hasRole('" + SecurityRoles.R_EMPLOYEE + "')")
    public SalaryAdvanceResponse create(@Valid @RequestBody SalaryAdvanceRequest req) {
        UUID employeeId;
        String requestedBy;

        if (currentRequest.hasRole(SecurityRoles.R_EMPLOYEE) && !currentRequest.hasRole(SecurityRoles.R_HR_ADMIN)) {
            // EMPLOYEE: derive employeeId from context, ignore request body
            employeeId = employeeContext.currentEmployee().getId();
            requestedBy = currentRequest.username();
        } else {
            // HR: use request body
            employeeId = req.employeeId();
            requestedBy = currentRequest.username();
        }

        SalaryAdvance advance = service.request(employeeId, req.requestedAmount(), req.reason(), requestedBy);
        return SalaryAdvanceResponse.from(advance);
    }

    // Advance approval/rejection is an HR_ADMIN-only money decision (PAYROLL_SPECIALIST
    // may create/record advances but not approve them) — see the permission matrix.
    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public SalaryAdvanceResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveAdvanceRequest req) {
        SalaryAdvance advance = service.approve(id, req.approvedAmount(), req.repaymentYear(),
                req.repaymentMonth(), currentRequest.username());
        return SalaryAdvanceResponse.from(advance);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public SalaryAdvanceResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectAdvanceRequest req) {
        SalaryAdvance advance = service.reject(id, req.reason());
        return SalaryAdvanceResponse.from(advance);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL + " or hasRole('" + SecurityRoles.R_EMPLOYEE + "')")
    public SalaryAdvanceResponse cancel(@PathVariable UUID id) {
        // An EMPLOYEE may cancel only their OWN advance — verify ownership before acting so
        // a UUID cannot be used to cancel another employee's request.
        if (currentRequest.hasRole(SecurityRoles.R_EMPLOYEE) && !currentRequest.hasRole(SecurityRoles.R_HR_ADMIN)
                && !currentRequest.hasRole(SecurityRoles.R_PAYROLL_SPECIALIST)) {
            SalaryAdvance owned = service.get(id);
            UUID me = employeeContext.currentEmployee().getId();
            if (!owned.getEmployeeId().equals(me)) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot cancel another employee's advance");
            }
        }
        SalaryAdvance advance = service.cancel(id);
        return SalaryAdvanceResponse.from(advance);
    }
}
