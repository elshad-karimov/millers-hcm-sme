package az.millers.hcm.payroll.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.GLAccountMappingRequest;
import az.millers.hcm.payroll.domain.GLAccountMapping;
import az.millers.hcm.payroll.service.GLAccountMappingService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M355 — GL account mapping endpoints.
 */
@RestController
@RequestMapping("/api/payroll/gl/account-mappings")
public class GLAccountMappingController {

    private final GLAccountMappingService service;

    public GLAccountMappingController(GLAccountMappingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<List<GLAccountMapping>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public ResponseEntity<GLAccountMapping> create(@RequestBody GLAccountMappingRequest request) {
        GLAccountMappingService.MappingRequest req = new GLAccountMappingService.MappingRequest(
                request.componentKind(),
                request.componentCode(),
                request.accountType(),
                request.glAccountCode(),
                request.glAccountName()
        );
        return ResponseEntity.ok(service.create(req));
    }
}
