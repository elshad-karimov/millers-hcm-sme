package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.domain.PayrollCompTransfer;
import az.millers.hcm.compensation.service.PayrollCompTransferService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M369 — Compensation → Payroll Transfer API.
 */
@RestController
@RequestMapping("/api/compensation/transfers")
public class PayrollCompTransferController {

    private final PayrollCompTransferService service;

    public PayrollCompTransferController(PayrollCompTransferService service) {
        this.service = service;
    }

    /**
     * Transfer all APPROVED incentive/commission payouts to the specified payroll run.
     * Idempotent, skips terminated employees.
     */
    @PostMapping("/to-run/{payrollRunId}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION + " or " + SecurityRoles.WRITE_PAYROLL)
    public Map<String, Object> transferToRun(@PathVariable UUID payrollRunId) {
        return service.transferApprovedToRun(payrollRunId);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<PayrollCompTransfer> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }
}
