package az.millers.hcm.compensation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.domain.CommissionPlan;
import az.millers.hcm.compensation.domain.CommissionTier;
import az.millers.hcm.compensation.service.CommissionPlanService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M366 — Commission Plan controller.
 */
@RestController
@RequestMapping("/api/compensation/commission-plans")
public class CommissionPlanController {

    private final CommissionPlanService service;

    public CommissionPlanController(CommissionPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION_OR_HR)
    public List<CommissionPlan> list(@RequestParam(required = false) Boolean activeOnly) {
        return Boolean.TRUE.equals(activeOnly) ? service.listActive() : service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION_OR_HR)
    public CommissionPlan get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/tiers")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION_OR_HR)
    public List<CommissionTier> getTiers(@PathVariable UUID id) {
        return service.getTiers(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public CommissionPlan create(@RequestBody CreateCommissionPlanRequest req) {
        return service.create(req.code(), req.name(), req.basis(),
                req.flatRatePct(), req.tiered(), req.currency());
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public CommissionPlan update(@PathVariable UUID id, @RequestBody UpdateCommissionPlanRequest req) {
        return service.update(id, req.name(), req.basis(), req.flatRatePct(), req.tiered());
    }

    @PutMapping("/{id}/tiers")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public List<CommissionTier> updateTiers(@PathVariable UUID id, @RequestBody List<CommissionTier> tiers) {
        return service.updateTiers(id, tiers);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    public record CreateCommissionPlanRequest(String code, String name, String basis,
                                               BigDecimal flatRatePct, Boolean tiered, String currency) {}

    public record UpdateCommissionPlanRequest(String name, String basis,
                                               BigDecimal flatRatePct, Boolean tiered) {}
}
