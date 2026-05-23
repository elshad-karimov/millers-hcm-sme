package az.millers.hcm.payroll.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.BankAccountRequest;
import az.millers.hcm.payroll.api.dto.BankAccountResponse;
import az.millers.hcm.payroll.domain.BankAccount;
import az.millers.hcm.payroll.service.BankAccountService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payroll/bank-accounts")
public class BankAccountController {

    private final BankAccountService service;

    public BankAccountController(BankAccountService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','PAYROLL_SPECIALIST','AUDITOR')")
    public ResponseEntity<BankAccountResponse> get(@RequestParam UUID employeeId) {
        return service.findForEmployee(employeeId)
                .map(a -> ResponseEntity.ok(BankAccountResponse.from(a)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public BankAccountResponse upsert(@Valid @RequestBody BankAccountRequest req) {
        BankAccount a = new BankAccount();
        a.setEmployeeId(req.employeeId());
        a.setBankCode(req.bankCode());
        a.setBankName(req.bankName());
        a.setIban(req.iban());
        a.setAccountNumber(req.accountNumber());
        a.setCurrency(req.currency() == null ? "AZN" : req.currency());
        a.setActive(req.active() == null ? true : req.active());
        return BankAccountResponse.from(service.upsert(a));
    }
}
