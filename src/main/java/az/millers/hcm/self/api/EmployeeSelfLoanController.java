package az.millers.hcm.self.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.LoanInstallmentSchedule;
import az.millers.hcm.payroll.domain.LoanRequest;
import az.millers.hcm.payroll.service.LoanInstallmentService;
import az.millers.hcm.payroll.service.LoanRequestService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;

@RestController
@RequestMapping("/api/self/loans")
public class EmployeeSelfLoanController {

    private final LoanRequestService loanRequestService;
    private final LoanInstallmentService installmentService;
    private final EmployeeContextService employeeContext;
    private final CurrentRequest currentRequest;

    public EmployeeSelfLoanController(LoanRequestService loanRequestService,
                                      LoanInstallmentService installmentService,
                                      EmployeeContextService employeeContext,
                                      CurrentRequest currentRequest) {
        this.loanRequestService = loanRequestService;
        this.installmentService = installmentService;
        this.employeeContext = employeeContext;
        this.currentRequest = currentRequest;
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<LoanRequest> getMyLoans(@PathVariable UUID employeeId) {
        guardLoanAccess(employeeId);
        return loanRequestService.listByEmployee(employeeId);
    }

    @PostMapping("/{employeeId}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public LoanRequest submit(@PathVariable UUID employeeId, @RequestBody SubmitRequest req) {
        guardLoanAccess(employeeId);
        return loanRequestService.submit(employeeId, req.loanTypeId, req.requestedAmount, req.requestedMonths, req.purpose);
    }

    @GetMapping("/{employeeId}/requests/{loanRequestId}/installments")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<LoanInstallmentSchedule> getMyInstallments(@PathVariable UUID employeeId, @PathVariable UUID loanRequestId) {
        guardLoanAccess(employeeId);
        LoanRequest request = loanRequestService.get(loanRequestId);
        if (!request.getEmployeeId().equals(employeeId)) {
            throw new ResourceNotFoundException("Loan request not found");
        }
        return installmentService.getSchedule(loanRequestId);
    }

    /**
     * Loans are confidential — strict self-or-HR guard: the caller can access
     * loans ONLY if they are the employee themselves OR hold HR/payroll admin
     * roles. Managers cannot see their team's loans.
     */
    private void guardLoanAccess(UUID employeeId) {
        UUID currentEmployeeId = employeeContext.currentEmployee().getId();
        if (currentEmployeeId.equals(employeeId)) {
            return; // self access allowed
        }
        // Not self — check if caller has HR/payroll admin roles
        if (currentRequest.hasRole(SecurityRoles.R_HR_ADMIN) ||
            currentRequest.hasRole(SecurityRoles.R_PAYROLL_SPECIALIST) ||
            currentRequest.hasRole(SecurityRoles.R_SYSTEM_ADMIN)) {
            return; // HR/payroll admin allowed
        }
        throw new ResourceNotFoundException("Loan request not found");
    }

    public record SubmitRequest(UUID loanTypeId, BigDecimal requestedAmount, Integer requestedMonths, String purpose) {}
}
