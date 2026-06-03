package az.millers.hcm.corehr.api;

import az.millers.hcm.security.SecurityRoles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.EmployeeResponse;
import az.millers.hcm.corehr.api.dto.RehireRequest;
import az.millers.hcm.corehr.service.RehireService;
import jakarta.validation.Valid;

/**
 * Rehire endpoint (M78 / P2-15). Separate from the standard employee CRUD
 * surface so the audit story stays clean: hitting POST /api/employees/rehire
 * always lands as a REHIRE audit event.
 */
@RestController
@RequestMapping("/api/employees")
public class RehireController {

    private final RehireService service;

    public RehireController(RehireService service) {
        this.service = service;
    }

    @PostMapping("/rehire")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EmployeeResponse rehire(@Valid @RequestBody RehireRequest req) {
        return EmployeeResponse.from(service.rehire(req));
    }
}
