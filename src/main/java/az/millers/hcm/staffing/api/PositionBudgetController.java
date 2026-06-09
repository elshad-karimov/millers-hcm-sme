package az.millers.hcm.staffing.api;

import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionBudgetDtos.BudgetRequest;
import az.millers.hcm.staffing.api.dto.PositionBudgetDtos.BudgetResponse;
import az.millers.hcm.staffing.service.PositionBudgetService;
import jakarta.validation.Valid;

/**
 * M244 — versioned budget rows for a position.
 *
 * Read: any HR / Finance reader. Write: HR_ADMIN + FINANCE_USER only —
 * payroll budget is a finance concern, not a routine HR operation.
 */
@RestController
@RequestMapping("/api/positions/{positionId}/budgets")
public class PositionBudgetController {

    private final PositionBudgetService service;

    public PositionBudgetController(PositionBudgetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public List<BudgetResponse> list(@PathVariable UUID positionId) {
        return service.listForPosition(positionId).stream()
                .map(BudgetResponse::from)
                .toList();
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public BudgetResponse current(@PathVariable UUID positionId) {
        return service.currentBudget(positionId)
                .map(BudgetResponse::from)
                .orElse(null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public BudgetResponse create(@PathVariable UUID positionId,
                                  @Valid @RequestBody BudgetRequest req) {
        return BudgetResponse.from(service.create(positionId, req.toEntity()));
    }

    @PutMapping("/{budgetId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public BudgetResponse update(@PathVariable UUID positionId,
                                  @PathVariable UUID budgetId,
                                  @Valid @RequestBody BudgetRequest req) {
        return BudgetResponse.from(service.update(budgetId, req.toEntity()));
    }

    @DeleteMapping("/{budgetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID positionId, @PathVariable UUID budgetId) {
        service.delete(budgetId);
    }
}
