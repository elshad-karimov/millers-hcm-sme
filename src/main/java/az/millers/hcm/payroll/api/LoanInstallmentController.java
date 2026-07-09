package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.payroll.domain.LoanInstallmentSchedule;
import az.millers.hcm.payroll.service.LoanInstallmentService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M462 — Loan installment schedule (HR/Finance GET + ESS own statement).
 */
@RestController
@RequestMapping("/api/payroll/loan-installments")
public class LoanInstallmentController {

    private final LoanInstallmentService service;

    public LoanInstallmentController(LoanInstallmentService service) {
        this.service = service;
    }

    @GetMapping("/loan-requests/{loanRequestId}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<LoanInstallmentSchedule> getSchedule(@PathVariable UUID loanRequestId) {
        return service.getSchedule(loanRequestId);
    }
}
