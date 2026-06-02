package az.millers.hcm.payroll.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.BankAccountRequest;
import az.millers.hcm.payroll.api.dto.BankAccountResponse;
import az.millers.hcm.payroll.service.BankAccountService;
import jakarta.validation.Valid;

/**
 * REST surface for {@link az.millers.hcm.payroll.domain.BankAccount} (M74).
 *
 * <p>Migrated from single-row upsert (POST returns the one account per
 * employee) to multi-row CRUD: list / create / update / delete operations
 * support the salary-split feature. The {@code GET ?employeeId=...} endpoint
 * now returns an array; pre-M74 frontend callers that need the single primary
 * account filter the array client-side.
 */
@RestController
@RequestMapping("/api/payroll/bank-accounts")
public class BankAccountController {

    private static final String READ_ROLES =
            "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','PAYROLL_SPECIALIST','AUDITOR')";
    private static final String WRITE_ROLES = "hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')";

    private final BankAccountService service;

    public BankAccountController(BankAccountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<BankAccountResponse> list(
            @RequestParam UUID employeeId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return service.listForEmployee(employeeId, activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public BankAccountResponse create(@Valid @RequestBody BankAccountRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public BankAccountResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody BankAccountRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
