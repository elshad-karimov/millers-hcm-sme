package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.payroll.domain.LoanType;
import az.millers.hcm.payroll.service.LoanTypeService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M460 — Loan type CRUD (HR_ADMIN).
 */
@RestController
@RequestMapping("/api/payroll/loan-types")
public class LoanTypeController {

    private final LoanTypeService service;

    public LoanTypeController(LoanTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<LoanType> listActive() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public LoanType get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LoanType create(@RequestBody LoanType type) {
        return service.create(type);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LoanType update(@PathVariable UUID id, @RequestBody LoanType type) {
        return service.update(id, type);
    }
}
