package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.ContractRequest;
import az.millers.hcm.lifecycle.api.dto.ContractResponse;
import az.millers.hcm.lifecycle.service.EmploymentContractService;

/**
 * REST surface for {@link az.millers.hcm.lifecycle.domain.EmploymentContract}
 * (M64 / P1-03).
 *
 * <p>Two URL prefixes:
 * <ul>
 *   <li>{@code /api/employees/{employeeId}/contracts} — employee-anchored
 *       listing + create. The shape mirrors the M63 personal-details endpoints
 *       so the upcoming Contracts tab on the employee profile (M70) plugs in
 *       cleanly.</li>
 *   <li>{@code /api/contracts/{id}/...} — single-contract operations
 *       (transitions, signatures, termination).</li>
 * </ul>
 *
 * <p>Roles:
 * <ul>
 *   <li>Read: HR_ADMIN / HR_SPECIALIST / DEPARTMENT_MANAGER (scoped) /
 *       SYSTEM_ADMIN / AUDITOR.</li>
 *   <li>Write: HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN. (Sign-as-employee
 *       is shipped as an HR-driven endpoint here — the self-service path
 *       comes in Phase 2.)</li>
 * </ul>
 */
@RestController
public class EmploymentContractController {

    private static final String READ_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN','AUDITOR')";
    private static final String WRITE_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')";

    private final EmploymentContractService service;

    public EmploymentContractController(EmploymentContractService service) {
        this.service = service;
    }

    // ── Employee-anchored ─────────────────────────────────────────────────────

    @GetMapping("/api/employees/{employeeId}/contracts")
    @PreAuthorize(READ_ROLES)
    public List<ContractResponse> listForEmployee(@PathVariable UUID employeeId) {
        return service.listFor(employeeId);
    }

    @GetMapping("/api/employees/{employeeId}/contracts/current")
    @PreAuthorize(READ_ROLES)
    public ContractResponse currentForEmployee(@PathVariable UUID employeeId) {
        return service.currentFor(employeeId);
    }

    @PostMapping("/api/employees/{employeeId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public ContractResponse create(@PathVariable UUID employeeId,
                                    @RequestBody @Valid ContractRequest req) {
        return service.create(employeeId, req);
    }

    // ── Single-contract operations ────────────────────────────────────────────

    @GetMapping("/api/contracts/{id}")
    @PreAuthorize(READ_ROLES)
    public ContractResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/api/contracts/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ContractResponse update(@PathVariable UUID id,
                                    @RequestBody @Valid ContractRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/api/contracts/{id}/activate")
    @PreAuthorize(WRITE_ROLES)
    public ContractResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/api/contracts/{id}/sign-employee")
    @PreAuthorize(WRITE_ROLES)
    public ContractResponse signByEmployee(@PathVariable UUID id) {
        return service.signByEmployee(id);
    }

    @PostMapping("/api/contracts/{id}/sign-hr")
    @PreAuthorize(WRITE_ROLES)
    public ContractResponse signByHr(@PathVariable UUID id) {
        return service.signByHr(id);
    }

    @PostMapping("/api/contracts/{id}/terminate")
    @PreAuthorize(WRITE_ROLES)
    public ContractResponse terminate(@PathVariable UUID id,
                                       @RequestParam(required = false) String reason) {
        return service.terminate(id, reason);
    }
}
