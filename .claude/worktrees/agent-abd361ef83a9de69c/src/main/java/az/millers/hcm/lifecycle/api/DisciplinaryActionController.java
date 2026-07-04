package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.AppealRequest;
import az.millers.hcm.lifecycle.api.dto.DisciplinaryActionRequest;
import az.millers.hcm.lifecycle.api.dto.DisciplinaryActionResponse;
import az.millers.hcm.lifecycle.domain.DisciplinaryStatus;
import az.millers.hcm.lifecycle.service.DisciplinaryActionService;

/**
 * REST surface for {@link az.millers.hcm.lifecycle.domain.DisciplinaryAction}
 * (M67 / P1-12).
 *
 * <p>Read: HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN / AUDITOR.
 * DEPARTMENT_MANAGER is intentionally excluded — disciplinary records are
 * sensitive and HR-mediated; managers see only the outcomes (returns to
 * work, terminations) through their normal team views, not the underlying
 * disciplinary detail.
 *
 * <p>Write: HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN. The {@code /appeal}
 * endpoint additionally allows EMPLOYEE on their own action (handled
 * downstream once the M70 self-service path lands; for M67 the appeal is
 * HR-initiated on behalf of the employee).
 */
@RestController
@RequestMapping("/api/disciplinary")
public class DisciplinaryActionController {

    /** Centralised role sets — see {@link az.millers.hcm.security.SecurityRoles}. */
    private static final String READ_ROLES = az.millers.hcm.security.SecurityRoles.READ_HR;
    private static final String WRITE_ROLES = az.millers.hcm.security.SecurityRoles.WRITE_HR;

    private final DisciplinaryActionService service;

    public DisciplinaryActionController(DisciplinaryActionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public Page<DisciplinaryActionResponse> list(
            @RequestParam(required = false) DisciplinaryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "actionDate"));
        return service.list(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public DisciplinaryActionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize(READ_ROLES)
    public List<DisciplinaryActionResponse> listForEmployee(@PathVariable UUID employeeId) {
        return service.listForEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public DisciplinaryActionResponse create(@RequestBody @Valid DisciplinaryActionRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(WRITE_ROLES)
    public DisciplinaryActionResponse submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize(WRITE_ROLES)
    public DisciplinaryActionResponse issue(@PathVariable UUID id) {
        return service.issue(id);
    }

    @PostMapping("/{id}/appeal")
    @PreAuthorize(WRITE_ROLES)
    public DisciplinaryActionResponse appeal(@PathVariable UUID id,
                                              @RequestBody @Valid AppealRequest req) {
        return service.appeal(id, req);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(WRITE_ROLES)
    public DisciplinaryActionResponse close(@PathVariable UUID id,
                                             @RequestParam(required = false) String appealOutcome) {
        return service.close(id, appealOutcome);
    }
}
