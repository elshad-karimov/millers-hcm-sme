package az.millers.hcm.self.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.payroll.domain.LoanInstallmentSchedule;
import az.millers.hcm.payroll.domain.LoanRequest;
import az.millers.hcm.payroll.service.LoanInstallmentService;
import az.millers.hcm.payroll.service.LoanRequestService;

@RestController
@RequestMapping("/api/self/loans")
public class EmployeeSelfLoanController {

    private final LoanRequestService loanRequestService;
    private final LoanInstallmentService installmentService;

    public EmployeeSelfLoanController(LoanRequestService loanRequestService, LoanInstallmentService installmentService) {
        this.loanRequestService = loanRequestService;
        this.installmentService = installmentService;
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<LoanRequest> getMyLoans(@PathVariable UUID employeeId) {
        return loanRequestService.listByEmployee(employeeId);
    }

    @PostMapping("/{employeeId}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public LoanRequest submit(@PathVariable UUID employeeId, @RequestBody SubmitRequest req) {
        return loanRequestService.submit(employeeId, req.loanTypeId, req.requestedAmount, req.requestedMonths, req.purpose);
    }

    @GetMapping("/{employeeId}/requests/{loanRequestId}/installments")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<LoanInstallmentSchedule> getMyInstallments(@PathVariable UUID employeeId, @PathVariable UUID loanRequestId) {
        LoanRequest request = loanRequestService.get(loanRequestId);
        if (!request.getEmployeeId().equals(employeeId)) {
            throw new az.millers.hcm.common.ResourceNotFoundException("Loan request not found");
        }
        return installmentService.getSchedule(loanRequestId);
    }

    public record SubmitRequest(UUID loanTypeId, BigDecimal requestedAmount, Integer requestedMonths, String purpose) {}
}
