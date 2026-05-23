package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.PageResponse;
import az.millers.hcm.corehr.api.dto.AuditEntryResponse;
import az.millers.hcm.corehr.api.dto.EmployeeRequest;
import az.millers.hcm.corehr.api.dto.EmployeeResponse;
import az.millers.hcm.corehr.api.dto.StatusChangeRequest;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.service.EmployeeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuditService auditService;

    public EmployeeController(EmployeeService employeeService, AuditService auditService) {
        this.employeeService = employeeService;
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public PageResponse<EmployeeResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EmploymentStatus status) {

        var pageable = PageRequest.of(page, size, Sort.by("lastName").ascending());
        Page<?> result = employeeService.list(search, status, pageable);
        @SuppressWarnings("unchecked")
        Page<az.millers.hcm.corehr.domain.Employee> employees =
                (Page<az.millers.hcm.corehr.domain.Employee>) result;
        return PageResponse.of(employees, EmployeeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public EmployeeResponse get(@PathVariable UUID id) {
        return EmployeeResponse.from(employeeService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
        return EmployeeResponse.from(employeeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
        return EmployeeResponse.from(employeeService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public EmployeeResponse changeStatus(@PathVariable UUID id,
                                         @Valid @RequestBody StatusChangeRequest request) {
        return EmployeeResponse.from(
                employeeService.changeStatus(id, request.newStatus(), request.reason()));
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','AUDITOR')")
    public List<AuditEntryResponse> auditHistory(@PathVariable UUID id) {
        return auditService.history("Employee", id.toString()).stream()
                .map(AuditEntryResponse::from)
                .toList();
    }
}
