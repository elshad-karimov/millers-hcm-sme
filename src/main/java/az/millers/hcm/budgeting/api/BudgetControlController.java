package az.millers.hcm.budgeting.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.budgeting.domain.BudgetControlRule;
import az.millers.hcm.budgeting.domain.ControlAction;
import az.millers.hcm.budgeting.domain.TriggerPoint;
import az.millers.hcm.budgeting.service.BudgetControlService;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_20 M428 — Budget control rules REST API.
 * HR_ADMIN only.
 */
@RestController
@RequestMapping("/api/budgets/control-rules")
public class BudgetControlController {

    private final BudgetControlService service;

    public BudgetControlController(BudgetControlService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<BudgetControlRule> listRules() {
        return service.listRules();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BudgetControlRule upsertRule(@RequestBody RuleRequest req) {
        return service.upsertRule(req.triggerPoint(), req.action(), req.thresholdPct());
    }

    public record RuleRequest(TriggerPoint triggerPoint, ControlAction action, BigDecimal thresholdPct) {}
}
