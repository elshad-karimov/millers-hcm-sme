package az.millers.hcm.engagement.api;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.engagement.api.dto.*;
import az.millers.hcm.engagement.domain.RewardBudget;
import az.millers.hcm.engagement.domain.RewardCatalog;
import az.millers.hcm.engagement.domain.RewardRedemption;
import az.millers.hcm.engagement.domain.RewardWallet;
import az.millers.hcm.engagement.service.RewardPointsService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * M481: Reward points & redemption endpoints.
 */
@RestController
@RequestMapping("/api/engagement/rewards")
public class RewardPointsController {

    private final RewardPointsService service;
    private final EmployeeContextService employeeContext;
    private final CurrentRequest currentRequest;

    public RewardPointsController(RewardPointsService service, EmployeeContextService employeeContext, CurrentRequest currentRequest) {
        this.service = service;
        this.employeeContext = employeeContext;
        this.currentRequest = currentRequest;
    }

    // ───────────────────────────── Catalog ─────────────────────────────

    @GetMapping("/catalog")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<RewardCatalog> listCatalog(@RequestParam(required = false, defaultValue = "true") boolean activeOnly) {
        return service.listCatalog(activeOnly);
    }

    @GetMapping("/catalog/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public RewardCatalog getCatalog(@PathVariable UUID id) {
        return service.getCatalog(id);
    }

    @PostMapping("/catalog")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RewardCatalog createCatalog(@Valid @RequestBody RewardCatalogRequest req) {
        RewardCatalog item = new RewardCatalog();
        item.setCode(req.code());
        item.setName(req.name());
        item.setDescription(req.description());
        item.setPoints(req.points());
        item.setMonetaryValue(req.monetaryValue());
        item.setCategory(req.category());
        item.setTaxable(req.taxable() != null ? req.taxable() : false);
        item.setActive(req.active() != null ? req.active() : true);
        return service.createCatalog(item);
    }

    @PutMapping("/catalog/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RewardCatalog updateCatalog(@PathVariable UUID id, @Valid @RequestBody RewardCatalogRequest req) {
        RewardCatalog updated = new RewardCatalog();
        updated.setName(req.name());
        updated.setDescription(req.description());
        updated.setPoints(req.points());
        updated.setMonetaryValue(req.monetaryValue());
        updated.setCategory(req.category());
        updated.setTaxable(req.taxable() != null ? req.taxable() : false);
        updated.setActive(req.active() != null ? req.active() : true);
        return service.updateCatalog(id, updated);
    }

    // ───────────────────────────── Wallet ─────────────────────────────

    @GetMapping("/wallet/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public RewardWallet getWallet(@PathVariable UUID employeeId) {
        // IDOR guard: self-or-HR only (wallet is money data; managers do not get arbitrary access)
        boolean isSelf = employeeContext.currentEmployee().getId().equals(employeeId);
        boolean isHR = currentRequest.hasRole(SecurityRoles.R_SYSTEM_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_SPECIALIST);
        if (!isSelf && !isHR) {
            throw new ResourceNotFoundException("Wallet not found");
        }
        return service.getWallet(employeeId);
    }

    @PostMapping("/grant")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public void grantPoints(@Valid @RequestBody GrantPointsRequest req) {
        service.grantPoints(req.employeeId(), req.points(), req.note(), req.orgUnitId());
    }

    // ───────────────────────────── Redemptions ─────────────────────────────

    @GetMapping("/redemptions")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<RewardRedemption> listRedemptions(@RequestParam(required = false) String status) {
        return service.listRedemptions(status);
    }

    @GetMapping("/redemptions/my/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<RewardRedemption> listMyRedemptions(@PathVariable UUID employeeId) {
        // IDOR guard: self-or-HR only (redemption history is money data)
        boolean isSelf = employeeContext.currentEmployee().getId().equals(employeeId);
        boolean isHR = currentRequest.hasRole(SecurityRoles.R_SYSTEM_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_SPECIALIST);
        if (!isSelf && !isHR) {
            throw new ResourceNotFoundException("Redemptions not found");
        }
        return service.listMyRedemptions(employeeId);
    }

    @GetMapping("/redemptions/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public RewardRedemption getRedemption(@PathVariable UUID id) {
        return service.getRedemption(id);
    }

    @PostMapping("/redeem")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public RewardRedemption redeem(@Valid @RequestBody RedeemRewardRequest req) {
        // Force redeem as currentEmployee (employees can only redeem their own points)
        UUID employeeId = employeeContext.currentEmployee().getId();
        return service.redeem(employeeId, req.catalogItemId(), req.deliveryAddress());
    }

    @PostMapping("/redemptions/{id}/fulfill")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void fulfillRedemption(@PathVariable UUID id, @Valid @RequestBody FulfillRedemptionRequest req) {
        service.fulfillRedemption(id, req.payrollRunId());
    }

    @PostMapping("/redemptions/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void rejectRedemption(@PathVariable UUID id, @RequestParam String reason) {
        service.rejectRedemption(id, reason);
    }

    // ───────────────────────────── Budgets ─────────────────────────────

    @GetMapping("/budgets")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<RewardBudget> listBudgets(@RequestParam Integer year) {
        return service.listBudgets(year);
    }

    @PostMapping("/budgets")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RewardBudget createOrUpdateBudget(@Valid @RequestBody BudgetRequest req) {
        return service.createOrUpdateBudget(req.year(), req.orgUnitId(), req.totalAmount());
    }
}
