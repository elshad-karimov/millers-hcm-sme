package az.millers.hcm.budgeting.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.budgeting.api.dto.DepartmentVariance;
import az.millers.hcm.budgeting.service.BudgetVarianceService;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_20 M427 — Budget variance REST API.
 * HR/Finance full; DEPARTMENT_MANAGER sees only own org-unit variance.
 */
@RestController
@RequestMapping("/api/budgets/variance")
public class BudgetVarianceController {

    private final BudgetVarianceService service;

    public BudgetVarianceController(BudgetVarianceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','FINANCE_USER','DEPARTMENT_MANAGER')")
    public List<DepartmentVariance> variance(@RequestParam UUID cycleId) {
        return service.variance(cycleId);
    }
}
