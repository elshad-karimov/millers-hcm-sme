package az.millers.hcm.organization.api;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.service.DepartmentService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The department list, as master data.
 *
 * Reading is open to anyone who may see employees, because the employee screen
 * populates its Department picker from here. Maintaining the list is HR-admin
 * work, matching the org structure it writes into.
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    public record DepartmentResponse(UUID id, String code, String name) {
        static DepartmentResponse from(OrgUnit u) {
            return new DepartmentResponse(u.getId(), u.getCode(), u.getName());
        }
    }

    public record DepartmentRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String name) {
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<DepartmentResponse> list() {
        return service.list().stream().map(DepartmentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest req) {
        return DepartmentResponse.from(service.create(req.code(), req.name()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
    public DepartmentResponse rename(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest req) {
        return DepartmentResponse.from(service.rename(id, req.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
