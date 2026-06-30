package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.CreateLoanRequest;
import az.millers.hcm.payroll.api.dto.PayrollLoanResponse;
import az.millers.hcm.payroll.api.dto.WriteOffLoanRequest;
import az.millers.hcm.payroll.domain.PayrollLoan;
import az.millers.hcm.payroll.domain.PayrollLoanStatus;
import az.millers.hcm.payroll.repo.PayrollLoanRepository;
import az.millers.hcm.payroll.service.PayrollLoanService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M352 — Payroll loan management (HR-only).
 */
@RestController
@RequestMapping("/api/payroll/loans")
public class PayrollLoanController {

    private final PayrollLoanService service;
    private final PayrollLoanRepository loanRepo;
    private final CurrentRequest currentRequest;

    public PayrollLoanController(PayrollLoanService service,
                                  PayrollLoanRepository loanRepo,
                                  CurrentRequest currentRequest) {
        this.service = service;
        this.loanRepo = loanRepo;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<PayrollLoanResponse> list(@RequestParam(required = false) UUID employeeId,
                                           @RequestParam(required = false) String status) {
        if (employeeId != null && status != null) {
            return loanRepo.findByEmployeeIdAndStatus(employeeId, PayrollLoanStatus.valueOf(status)).stream()
                    .map(PayrollLoanResponse::from)
                    .toList();
        }
        if (employeeId != null) {
            return service.findByEmployeeId(employeeId).stream()
                    .map(PayrollLoanResponse::from)
                    .toList();
        }
        return service.findAll().stream()
                .map(PayrollLoanResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public PayrollLoanResponse get(@PathVariable UUID id) {
        return PayrollLoanResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public PayrollLoanResponse create(@Valid @RequestBody CreateLoanRequest req) {
        PayrollLoan loan = service.create(req.employeeId(), req.principalAmount(), req.monthlyInstallment(),
                req.startDeductionYear(), req.startDeductionMonth(), req.description(), currentRequest.username());
        return PayrollLoanResponse.from(loan);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public PayrollLoanResponse cancel(@PathVariable UUID id) {
        PayrollLoan loan = service.cancel(id);
        return PayrollLoanResponse.from(loan);
    }

    // Writing off an outstanding loan is a non-reversible financial concession —
    // restrict to HR_ADMIN / SYSTEM_ADMIN, not PAYROLL_SPECIALIST.
    @PostMapping("/{id}/write-off")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PayrollLoanResponse writeOff(@PathVariable UUID id, @Valid @RequestBody WriteOffLoanRequest req) {
        PayrollLoan loan = service.writeOff(id, req.reason());
        return PayrollLoanResponse.from(loan);
    }
}
