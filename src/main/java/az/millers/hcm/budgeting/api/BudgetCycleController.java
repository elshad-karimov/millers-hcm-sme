package az.millers.hcm.budgeting.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.budgeting.api.dto.BudgetCycleResponse;
import az.millers.hcm.budgeting.domain.BudgetCycle;
import az.millers.hcm.budgeting.domain.BudgetCycleStatus;
import az.millers.hcm.budgeting.service.BudgetCycleService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * HCM_20 M425 — Budget cycle REST API.
 * HR/Finance confidential.
 */
@RestController
@RequestMapping("/api/budgets/cycles")
public class BudgetCycleController {

    private final BudgetCycleService service;

    public BudgetCycleController(BudgetCycleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<BudgetCycleResponse> list(@RequestParam(required = false) BudgetCycleStatus status) {
        return (status == null ? service.listAll() : service.listByStatus(status)).stream()
                .map(BudgetCycleResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public BudgetCycleResponse get(@PathVariable UUID id) {
        return BudgetCycleResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BudgetCycleResponse create(@Valid @RequestBody BudgetCycle cycle) {
        return BudgetCycleResponse.from(service.create(cycle));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BudgetCycleResponse update(@PathVariable UUID id, @Valid @RequestBody BudgetCycle cycle) {
        return BudgetCycleResponse.from(service.update(id, cycle));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BudgetCycleResponse updateStatus(@PathVariable UUID id, @RequestBody StatusRequest req) {
        return BudgetCycleResponse.from(service.updateStatus(id, req.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    public record StatusRequest(BudgetCycleStatus status) {}
}
