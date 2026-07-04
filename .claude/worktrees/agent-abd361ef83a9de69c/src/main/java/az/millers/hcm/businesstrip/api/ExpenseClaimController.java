package az.millers.hcm.businesstrip.api;

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

import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.ClaimResponse;
import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.CreateClaimRequest;
import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.RejectRequest;
import az.millers.hcm.businesstrip.domain.ClaimStatus;
import az.millers.hcm.businesstrip.service.ExpenseClaimService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Expense claim lifecycle endpoints (M104).
 * Employees create/submit their own claims; HR approves / rejects / marks paid.
 */
@RestController
@RequestMapping("/api/expense-claims")
public class ExpenseClaimController {

    private static final String EMPLOYEE_WRITE = "isAuthenticated()";
    private static final String HR_READ  = SecurityRoles.READ_HR;
    private static final String HR_WRITE = SecurityRoles.WRITE_HR;
    private static final String ADMIN_WRITE = SecurityRoles.WRITE_HR_ADMIN_ONLY;

    private final ExpenseClaimService service;

    public ExpenseClaimController(ExpenseClaimService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EMPLOYEE_WRITE)
    public ClaimResponse create(@RequestBody CreateClaimRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(EMPLOYEE_WRITE)
    public ClaimResponse submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(HR_WRITE)
    public ClaimResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(HR_WRITE)
    public ClaimResponse reject(@PathVariable UUID id,
                                 @RequestBody(required = false) RejectRequest req) {
        return service.reject(id, req);
    }

    @PostMapping("/{id}/paid")
    @PreAuthorize(ADMIN_WRITE)
    public ClaimResponse markPaid(@PathVariable UUID id) {
        return service.markPaid(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize(HR_READ)
    public ClaimResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize(HR_READ)
    public List<ClaimResponse> forEmployee(@PathVariable UUID employeeId) {
        return service.forEmployee(employeeId);
    }

    @GetMapping("/trips/{tripId}")
    @PreAuthorize(HR_READ)
    public List<ClaimResponse> forTrip(@PathVariable UUID tripId) {
        return service.forTrip(tripId);
    }

    @GetMapping
    @PreAuthorize(HR_READ)
    public List<ClaimResponse> list(
            @RequestParam(required = false) ClaimStatus status) {
        return status != null ? service.byStatus(status) : service.byStatus(null);
    }
}
