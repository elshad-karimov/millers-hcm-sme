package az.millers.hcm.budgeting.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.budgeting.api.dto.DepartmentBudgetResponse;
import az.millers.hcm.budgeting.domain.DepartmentBudget;
import az.millers.hcm.budgeting.service.DepartmentBudgetService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * HCM_20 M425 — Department budget REST API.
 * HR/Finance confidential.
 */
@RestController
@RequestMapping("/api/budgets/departments")
public class DepartmentBudgetController {

    private final DepartmentBudgetService service;

    public DepartmentBudgetController(DepartmentBudgetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<DepartmentBudgetResponse> listByCycle(@RequestParam UUID cycleId) {
        return service.listByCycle(cycleId).stream()
                .map(DepartmentBudgetResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public DepartmentBudgetResponse get(@PathVariable UUID id) {
        return DepartmentBudgetResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DepartmentBudgetResponse upsert(@RequestParam UUID cycleId, @Valid @RequestBody DepartmentBudget budget) {
        return DepartmentBudgetResponse.from(service.upsert(cycleId, budget));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DepartmentBudgetResponse update(@PathVariable UUID id, @Valid @RequestBody DepartmentBudget budget) {
        budget.setId(id);
        return DepartmentBudgetResponse.from(service.upsert(budget.getCycleId(), budget));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DepartmentBudgetResponse submit(@PathVariable UUID id) {
        return DepartmentBudgetResponse.from(service.submit(id));
    }
}
