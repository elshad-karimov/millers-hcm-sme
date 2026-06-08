package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.EmployeeDocumentRequest;
import az.millers.hcm.corehr.api.dto.EmployeeDocumentResponse;
import az.millers.hcm.corehr.service.EmployeeDocumentService;
import az.millers.hcm.security.SecurityRoles;

/**
 * REST surface for employee document management (M169 / PRD §8.1.3).
 *
 * <p>Routes: {@code /api/employees/{employeeId}/documents}
 *
 * <p>Read access: all HR roles + DEPARTMENT_MANAGER (scope-limited).
 * Write access: HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN.
 */
@RestController
public class EmployeeDocumentController {

    private static final String READ_ROLES  = SecurityRoles.READ_HR_PLUS_MANAGERS;
    private static final String WRITE_ROLES = SecurityRoles.WRITE_HR;

    private final EmployeeDocumentService service;

    public EmployeeDocumentController(EmployeeDocumentService service) {
        this.service = service;
    }

    @GetMapping("/api/employees/{employeeId}/documents")
    @PreAuthorize(READ_ROLES)
    public List<EmployeeDocumentResponse> list(@PathVariable UUID employeeId) {
        return service.listFor(employeeId);
    }

    @GetMapping("/api/employees/{employeeId}/documents/{docId}")
    @PreAuthorize(READ_ROLES)
    public EmployeeDocumentResponse get(@PathVariable UUID employeeId,
                                         @PathVariable UUID docId) {
        return service.get(docId);
    }

    @PostMapping("/api/employees/{employeeId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public EmployeeDocumentResponse create(@PathVariable UUID employeeId,
                                            @RequestBody @Valid EmployeeDocumentRequest req) {
        return service.create(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/documents/{docId}")
    @PreAuthorize(WRITE_ROLES)
    public EmployeeDocumentResponse update(@PathVariable UUID employeeId,
                                            @PathVariable UUID docId,
                                            @RequestBody @Valid EmployeeDocumentRequest req) {
        return service.update(docId, req);
    }

    @DeleteMapping("/api/employees/{employeeId}/documents/{docId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void delete(@PathVariable UUID employeeId, @PathVariable UUID docId) {
        service.delete(docId);
    }
}
