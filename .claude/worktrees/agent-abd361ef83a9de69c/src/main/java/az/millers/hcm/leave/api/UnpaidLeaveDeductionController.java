package az.millers.hcm.leave.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.UnpaidDeductionSyncResult;
import az.millers.hcm.leave.service.UnpaidLeaveDeductionService;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/unpaid-deductions")
public class UnpaidLeaveDeductionController {

    private final UnpaidLeaveDeductionService service;

    public UnpaidLeaveDeductionController(UnpaidLeaveDeductionService service) {
        this.service = service;
    }

    /**
     * Trigger sync: scan approved unpaid leave in [year, month] and create deduction rows.
     * Idempotent — safe to run multiple times for the same period.
     */
    @PostMapping("/sync")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<UnpaidDeductionSyncResult> sync(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "22") int workingDaysPerMonth) {
        return ResponseEntity.ok(service.syncPeriod(year, month, workingDaysPerMonth));
    }

    /** List all UNPAID_LEAVE deduction rows for a specific employee. */
    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<PayrollDeduction>> listForEmployee(@RequestParam UUID employeeId) {
        return ResponseEntity.ok(service.listForEmployee(employeeId));
    }
}
