package az.millers.hcm.leave.api;

import java.time.Year;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.LeaveBalanceAdjustment;
import az.millers.hcm.leave.api.dto.LeaveBalanceResponse;
import az.millers.hcm.leave.service.LeaveBalanceService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/leave/balances")
public class LeaveBalanceController {

    private final LeaveBalanceService service;

    public LeaveBalanceController(LeaveBalanceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR')")
    public List<LeaveBalanceResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) Integer year) {
        int y = year != null ? year : Year.now().getValue();
        var rows = employeeId != null
                ? service.listForEmployee(employeeId, y)
                : service.listForYear(y);
        return rows.stream().map(LeaveBalanceResponse::from).toList();
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public LeaveBalanceResponse adjust(@Valid @RequestBody LeaveBalanceAdjustment req) {
        return LeaveBalanceResponse.from(service.applyAdjustment(req));
    }
}
