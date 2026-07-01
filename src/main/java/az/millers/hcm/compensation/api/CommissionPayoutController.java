package az.millers.hcm.compensation.api;

import java.math.BigDecimal;
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

import az.millers.hcm.compensation.domain.CommissionPayout;
import az.millers.hcm.compensation.service.CommissionPayoutService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M366 — Commission Payout controller.
 */
@RestController
@RequestMapping("/api/compensation/commission-payouts")
public class CommissionPayoutController {

    private final CommissionPayoutService service;

    public CommissionPayoutController(CommissionPayoutService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION_OR_HR)
    public List<CommissionPayout> list(@RequestParam(required = false) UUID planId,
                                        @RequestParam(required = false) UUID employeeId,
                                        @RequestParam(required = false) String status) {
        return service.list(planId, employeeId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION_OR_HR)
    public CommissionPayout get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public CommissionPayout create(@RequestBody CreateCommissionPayoutRequest req) {
        return service.create(req.planId(), req.employeeId(), req.period(), req.salesAmount());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public CommissionPayout approve(@PathVariable UUID id, @RequestBody ApprovePayoutRequest req) {
        return service.approve(id, req.approvedBy());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public void cancel(@PathVariable UUID id) {
        service.cancel(id);
    }

    public record CreateCommissionPayoutRequest(UUID planId, UUID employeeId, String period,
                                                  BigDecimal salesAmount) {}

    public record ApprovePayoutRequest(String approvedBy) {}
}
