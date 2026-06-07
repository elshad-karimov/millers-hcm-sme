package az.millers.hcm.corehr.api;

import az.millers.hcm.security.SecurityRoles;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
import az.millers.hcm.corehr.api.dto.EmployeeSearchFilter;
import az.millers.hcm.corehr.api.dto.StatusChangeRequest;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.domain.MaritalStatus;
import az.millers.hcm.corehr.service.EmployeeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuditService auditService;
    /** M132 — QR code generator for badge / verification scans. */
    private final az.millers.hcm.corehr.service.EmployeeQrCodeService qrCodeService;

    public EmployeeController(EmployeeService employeeService,
                              AuditService auditService,
                              az.millers.hcm.corehr.service.EmployeeQrCodeService qrCodeService) {
        this.employeeService = employeeService;
        this.auditService = auditService;
        this.qrCodeService = qrCodeService;
    }

    /**
     * M69 / P1-15 — advanced search. Every filter param is optional; the
     * service AND-s every supplied dimension. The legacy {@code search} +
     * {@code status} params still work — when only those are supplied the
     * filter is functionally identical to the pre-M69 behaviour.
     */
    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PageResponse<EmployeeResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            // Multi-valued: ?status=ACTIVE&status=ON_PROBATION. Single-value
            // ?status=ACTIVE remains backward-compatible.
            @RequestParam(required = false) Set<EmploymentStatus> status,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) MaritalStatus maritalStatus,
            @RequestParam(required = false) UUID departmentOrgUnitId,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) UUID positionId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID leaveGroupId,
            @RequestParam(required = false) String costCentre,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hireDateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hireDateTo) {

        var pageable = PageRequest.of(page, size, Sort.by("lastName").ascending());
        EmployeeSearchFilter filter = new EmployeeSearchFilter(
                search, status, employmentType, nationality, maritalStatus,
                departmentOrgUnitId, departmentName, positionId, managerId,
                leaveGroupId, costCentre, hireDateFrom, hireDateTo);
        Page<az.millers.hcm.corehr.domain.Employee> employees =
                employeeService.list(filter, pageable);
        return PageResponse.of(employees, EmployeeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
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

    /**
     * M132 — PNG badge QR code. {@code size} is clamped to
     * {@code [50, 1024]}; default 300. Open to any authenticated user so
     * the directory can show the badge inline; payload encodes only
     * employee id + employee number — no PII.
     */
    @GetMapping(value = "/{id}/qr", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("isAuthenticated()")
    public org.springframework.http.ResponseEntity<byte[]> qr(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "300") int size) {
        byte[] png = qrCodeService.renderPng(id, size);
        return org.springframework.http.ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=300")
                .body(png);
    }
}
