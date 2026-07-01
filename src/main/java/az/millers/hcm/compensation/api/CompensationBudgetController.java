package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.Map;
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

import az.millers.hcm.compensation.api.dto.CompensationBudgetDto;
import az.millers.hcm.compensation.domain.BudgetScopeType;
import az.millers.hcm.compensation.domain.BudgetType;
import az.millers.hcm.compensation.domain.CompensationBudget;
import az.millers.hcm.compensation.service.CompensationBudgetService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M364 — Compensation budget endpoints.
 */
@RestController
@RequestMapping("/api/compensation/budgets")
public class CompensationBudgetController {

    private final CompensationBudgetService service;

    public CompensationBudgetController(CompensationBudgetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<CompensationBudgetDto> list(@RequestParam(required = false) BudgetType budgetType,
                                             @RequestParam(required = false) BudgetScopeType scopeType) {
        return service.list(budgetType, scopeType).stream()
                .map(CompensationBudgetDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public CompensationBudgetDto get(@PathVariable UUID id) {
        return CompensationBudgetDto.from(service.get(id));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public Map<String, Object> status(@PathVariable UUID id) {
        return service.status(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.CREATED)
    public CompensationBudgetDto create(@RequestBody CompensationBudgetDto req) {
        CompensationBudget budget = service.create(
                req.budgetType(),
                req.scopeType(),
                req.scopeRef(),
                req.amount(),
                req.currency(),
                req.cycleId(),
                req.effectiveFrom(),
                req.effectiveTo()
        );
        return CompensationBudgetDto.from(budget);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public CompensationBudgetDto update(@PathVariable UUID id, @RequestBody CompensationBudgetDto req) {
        CompensationBudget budget = service.update(id, req.amount(), req.effectiveFrom(), req.effectiveTo());
        return CompensationBudgetDto.from(budget);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deactivate(id);
    }
}
