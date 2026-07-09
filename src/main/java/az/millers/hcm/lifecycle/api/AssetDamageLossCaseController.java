package az.millers.hcm.lifecycle.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCase;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCaseType;
import az.millers.hcm.lifecycle.service.AssetDamageLossCaseService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M458 — Asset damage/loss cases (HR/Finance-only amounts).
 */
@RestController
@RequestMapping("/api/assets/damage-loss-cases")
public class AssetDamageLossCaseController {

    private final AssetDamageLossCaseService service;

    public AssetDamageLossCaseController(AssetDamageLossCaseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AssetDamageLossCase> listAll() {
        return service.listAll();
    }

    @GetMapping("/open")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AssetDamageLossCase> listOpen() {
        return service.listOpen();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public AssetDamageLossCase get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AssetDamageLossCase report(@RequestBody ReportRequest req) {
        return service.report(req.assetId, req.employeeId, req.caseType, req.description, req.estimatedAmount);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetDamageLossCase approve(@PathVariable UUID id, @RequestBody ApproveRequest req) {
        return service.approve(id, req.deductionProposed, req.deductionAmount);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetDamageLossCase reject(@PathVariable UUID id, @RequestBody RejectRequest req) {
        return service.reject(id, req.reason);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetDamageLossCase close(@PathVariable UUID id, @RequestBody CloseRequest req) {
        return service.close(id, req.notes);
    }

    public record ReportRequest(UUID assetId, UUID employeeId, AssetDamageLossCaseType caseType, String description, BigDecimal estimatedAmount) {}
    public record ApproveRequest(Boolean deductionProposed, BigDecimal deductionAmount) {}
    public record RejectRequest(String reason) {}
    public record CloseRequest(String notes) {}
}
