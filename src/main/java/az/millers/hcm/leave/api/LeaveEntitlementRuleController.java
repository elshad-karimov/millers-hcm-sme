package az.millers.hcm.leave.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.leave.domain.LeaveEntitlementRule;
import az.millers.hcm.leave.service.LeaveEntitlementRuleService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/types/{typeId}/entitlement-rules")
public class LeaveEntitlementRuleController {

    private final LeaveEntitlementRuleService service;

    public LeaveEntitlementRuleController(LeaveEntitlementRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<LeaveEntitlementRule> list(@PathVariable UUID typeId) {
        return service.list(typeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeaveEntitlementRule create(@PathVariable UUID typeId,
                                       @RequestBody LeaveEntitlementRule req) {
        return service.create(typeId, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeaveEntitlementRule update(@PathVariable UUID typeId,
                                       @PathVariable UUID id,
                                       @RequestBody LeaveEntitlementRule req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void toggleActive(@PathVariable UUID typeId, @PathVariable UUID id) {
        service.toggleActive(id);
    }

    /** Preview: returns the resolved annual entitlement for a given profile. */
    @GetMapping("/resolve")
    @PreAuthorize(SecurityRoles.READ_HR)
    public Map<String, Object> resolve(@PathVariable UUID typeId,
                                       @RequestParam EmploymentType employmentType,
                                       @RequestParam int tenureMonths) {
        Optional<BigDecimal> result = service.resolve(typeId, employmentType, tenureMonths);
        return result.<Map<String, Object>>map(days -> Map.of(
                "matched", true,
                "annualEntitlementDays", days,
                "monthlyAccrualDays", days.divide(java.math.BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP)))
                .orElse(Map.of("matched", false));
    }
}
