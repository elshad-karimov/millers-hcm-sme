package az.millers.hcm.security.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.security.service.PermissionMatrixService;

/**
 * M494 — Permission matrix view. Read-only for SYSTEM_ADMIN / AUDITOR / HR_ADMIN.
 */
@RestController
@RequestMapping("/api/security/permission-matrix")
public class PermissionMatrixController {

    private static final String MATRIX_VIEWERS =
        "hasAnyRole('" + SecurityRoles.R_SYSTEM_ADMIN + "','" +
        SecurityRoles.R_HR_ADMIN + "','" + SecurityRoles.R_AUDITOR + "')";

    private final PermissionMatrixService service;

    public PermissionMatrixController(PermissionMatrixService service) {
        this.service = service;
    }

    /**
     * Get the full permission matrix.
     */
    @GetMapping
    @PreAuthorize(MATRIX_VIEWERS)
    public PermissionMatrixService.PermissionMatrix getMatrix() {
        return service.getMatrix();
    }
}
