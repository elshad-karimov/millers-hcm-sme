package az.millers.hcm.payroll.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.payroll.domain.LoanRequest;
import az.millers.hcm.payroll.service.LoanRequestService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M461 — Loan request management (ESS submit, HR/Payroll approval).
 */
@RestController
@RequestMapping("/api/payroll/loan-requests")
public class LoanRequestController {

    private final LoanRequestService service;

    public LoanRequestController(LoanRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<LoanRequest> listAll() {
        return service.listAll();
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<LoanRequest> listByEmployee(@PathVariable UUID employeeId) {
        return service.listByEmployee(employeeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public LoanRequest get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN', 'PAYROLL_SPECIALIST')")
    public LoanRequest submit(@RequestBody SubmitRequest req) {
        return service.submit(req.employeeId, req.loanTypeId, req.requestedAmount, req.requestedMonths, req.purpose);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LoanRequest approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LoanRequest reject(@PathVariable UUID id, @RequestBody RejectRequest req) {
        return service.reject(id, req.reason);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public LoanRequest cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    public record SubmitRequest(UUID employeeId, UUID loanTypeId, BigDecimal requestedAmount, Integer requestedMonths, String purpose) {}
    public record RejectRequest(String reason) {}
}
