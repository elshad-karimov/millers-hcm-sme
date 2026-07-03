package az.millers.hcm.compbenefits.api;

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

import az.millers.hcm.compbenefits.api.dto.BenefitClaimDtos.ClaimRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitClaimDtos.ClaimResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitClaimDtos.PayRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitClaimDtos.ReviewRequest;
import az.millers.hcm.compbenefits.domain.BenefitClaimStatus;
import az.millers.hcm.compbenefits.service.BenefitClaimService;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;

/** Benefit claims API (HCM_11 M381/M382). */
@RestController
@RequestMapping("/api/compbenefits/claims")
public class BenefitClaimController {

    private final BenefitClaimService service;
    private final EmployeeContextService context;

    public BenefitClaimController(BenefitClaimService service, EmployeeContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_BENEFITS_PLUS_MANAGERS)
    public List<ClaimResponse> list(@RequestParam(required = false) BenefitClaimStatus status,
                                    @RequestParam(required = false) UUID employeeId) {
        if (employeeId != null) return service.listForEmployee(employeeId);
        return service.list(status);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<ClaimResponse> mine() {
        return service.listForEmployee(context.currentEmployee().getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ClaimResponse create(@Valid @RequestBody ClaimRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ClaimResponse submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ClaimResponse approve(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
        return service.approve(id, req == null ? null : req.approvedAmount(), req == null ? null : req.reviewNotes());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ClaimResponse reject(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
        return service.reject(id, req == null ? null : req.reviewNotes());
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ClaimResponse pay(@PathVariable UUID id, @RequestBody(required = false) PayRequest req) {
        return service.markPaid(id, req == null ? null : req.paymentReference());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ClaimResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
