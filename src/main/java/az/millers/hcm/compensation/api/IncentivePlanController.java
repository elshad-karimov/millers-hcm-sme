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

import az.millers.hcm.compensation.domain.IncentivePlan;
import az.millers.hcm.compensation.service.IncentivePlanService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M365 — Incentive Plan controller.
 */
@RestController
@RequestMapping("/api/compensation/incentive-plans")
public class IncentivePlanController {

    private final IncentivePlanService service;

    public IncentivePlanController(IncentivePlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION_OR_HR)
    public List<IncentivePlan> list(@RequestParam(required = false) Boolean activeOnly) {
        return Boolean.TRUE.equals(activeOnly) ? service.listActive() : service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION_OR_HR)
    public IncentivePlan get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public IncentivePlan create(@RequestBody CreateIncentivePlanRequest req) {
        return service.create(req.code(), req.name(), req.measure(),
                req.targetPct(), req.thresholdAchievement(), req.targetAchievement(),
                req.capAchievement(), req.maxPayoutPct(), req.currency());
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public IncentivePlan update(@PathVariable UUID id, @RequestBody UpdateIncentivePlanRequest req) {
        return service.update(id, req.name(), req.measure(), req.targetPct(),
                req.thresholdAchievement(), req.targetAchievement(), req.capAchievement(),
                req.maxPayoutPct());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION_OR_HR_ADMIN)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    public record CreateIncentivePlanRequest(String code, String name, String measure,
                                              BigDecimal targetPct, BigDecimal thresholdAchievement,
                                              BigDecimal targetAchievement, BigDecimal capAchievement,
                                              BigDecimal maxPayoutPct, String currency) {}

    public record UpdateIncentivePlanRequest(String name, String measure, BigDecimal targetPct,
                                              BigDecimal thresholdAchievement, BigDecimal targetAchievement,
                                              BigDecimal capAchievement, BigDecimal maxPayoutPct) {}
}
