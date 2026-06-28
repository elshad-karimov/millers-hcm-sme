package az.millers.hcm.leave.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.domain.LeaveBalanceLedger;
import az.millers.hcm.leave.service.LeaveLedgerService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/balances/ledger")
public class LeaveBalanceLedgerController {

    private final LeaveLedgerService service;

    public LeaveBalanceLedgerController(LeaveLedgerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<LeaveBalanceLedger> list(
            @RequestParam UUID employeeId,
            @RequestParam(required = false) UUID leaveTypeId,
            @RequestParam int year) {
        return service.listForEmployee(employeeId, leaveTypeId, year);
    }
}
