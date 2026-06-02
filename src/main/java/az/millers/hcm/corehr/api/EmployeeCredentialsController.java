package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.CertificationRequest;
import az.millers.hcm.corehr.api.dto.CertificationResponse;
import az.millers.hcm.corehr.api.dto.HealthRequest;
import az.millers.hcm.corehr.api.dto.HealthResponse;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.service.EmployeeCertificationService;
import az.millers.hcm.corehr.service.EmployeeHealthService;

/**
 * REST surface for external certifications + medical/health records (M65).
 *
 * <p>Role gates:
 * <ul>
 *   <li><strong>Certifications</strong> — read by HR_ADMIN / HR_SPECIALIST /
 *       DEPARTMENT_MANAGER (scoped) / SYSTEM_ADMIN / AUDITOR; write by
 *       HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN. License-number plaintext is
 *       gated to SYSTEM_ADMIN / HR_ADMIN / AUDITOR (same PII gate as
 *       identification documents).</li>
 *   <li><strong>Health</strong> — read AND write restricted to
 *       OCCUPATIONAL_HEALTH / HR_ADMIN / SYSTEM_ADMIN. Department managers
 *       and ordinary HR specialists cannot see this row at all (GDPR
 *       Article 9 — special category data). Adding the {@code OCCUPATIONAL_HEALTH}
 *       role to the Keycloak realm is a separate deployment step; the gate is
 *       in place now so the role can be granted without a code change.</li>
 * </ul>
 */
@RestController
public class EmployeeCredentialsController {

    private static final String CERT_READ_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN','AUDITOR')";
    private static final String CERT_WRITE_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')";
    private static final String HEALTH_ROLES =
            "hasAnyRole('OCCUPATIONAL_HEALTH','HR_ADMIN','SYSTEM_ADMIN')";

    private final EmployeeCertificationService certifications;
    private final EmployeeHealthService health;

    public EmployeeCredentialsController(EmployeeCertificationService certifications,
                                          EmployeeHealthService health) {
        this.certifications = certifications;
        this.health = health;
    }

    // ── Certifications ────────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/certifications")
    @PreAuthorize(CERT_READ_ROLES)
    public List<CertificationResponse> listCertifications(@PathVariable UUID employeeId) {
        return certifications.listFor(employeeId);
    }

    @PostMapping("/api/employees/{employeeId}/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CERT_WRITE_ROLES)
    public CertificationResponse createCertification(@PathVariable UUID employeeId,
                                                      @RequestBody @Valid CertificationRequest req) {
        return certifications.create(employeeId, req);
    }

    @PutMapping("/api/employees/{employeeId}/certifications/{certId}")
    @PreAuthorize(CERT_WRITE_ROLES)
    public CertificationResponse updateCertification(@PathVariable UUID employeeId,
                                                      @PathVariable UUID certId,
                                                      @RequestBody @Valid CertificationRequest req) {
        return certifications.update(certId, req);
    }

    @PostMapping("/api/employees/{employeeId}/certifications/{certId}/verify")
    @PreAuthorize(CERT_WRITE_ROLES)
    public CertificationResponse verifyCertification(@PathVariable UUID employeeId,
                                                      @PathVariable UUID certId,
                                                      @RequestParam VerificationStatus status) {
        return certifications.verify(certId, status);
    }

    @DeleteMapping("/api/employees/{employeeId}/certifications/{certId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CERT_WRITE_ROLES)
    public void deleteCertification(@PathVariable UUID employeeId, @PathVariable UUID certId) {
        certifications.delete(certId);
    }

    // ── Health (restricted) ───────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/health")
    @PreAuthorize(HEALTH_ROLES)
    public ResponseEntity<HealthResponse> getHealth(@PathVariable UUID employeeId) {
        return health.getFor(employeeId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/api/employees/{employeeId}/health")
    @PreAuthorize(HEALTH_ROLES)
    public HealthResponse upsertHealth(@PathVariable UUID employeeId,
                                        @RequestBody @Valid HealthRequest req) {
        return health.upsert(employeeId, req);
    }

    @DeleteMapping("/api/employees/{employeeId}/health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(HEALTH_ROLES)
    public void deleteHealth(@PathVariable UUID employeeId) {
        health.delete(employeeId);
    }
}
