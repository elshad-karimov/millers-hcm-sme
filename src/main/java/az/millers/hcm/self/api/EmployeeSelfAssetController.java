package az.millers.hcm.self.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.corehr.api.dto.AssetResponse;
import az.millers.hcm.corehr.service.EmployeeAssetService;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCaseType;
import az.millers.hcm.lifecycle.service.AssetDamageLossCaseService;

@RestController
@RequestMapping("/api/self/assets")
public class EmployeeSelfAssetController {

    private final EmployeeAssetService assetService;
    private final AssetDamageLossCaseService damageCaseService;

    public EmployeeSelfAssetController(EmployeeAssetService assetService, AssetDamageLossCaseService damageCaseService) {
        this.assetService = assetService;
        this.damageCaseService = damageCaseService;
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<AssetResponse> getMyAssets(@PathVariable UUID employeeId) {
        return assetService.listFor(employeeId, true);
    }

    @PostMapping("/{employeeId}/report-damage")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public void reportDamage(@PathVariable UUID employeeId, @RequestBody ReportDamageRequest req) {
        damageCaseService.report(req.assetId, employeeId, req.caseType, req.description, null);
    }

    public record ReportDamageRequest(UUID assetId, AssetDamageLossCaseType caseType, String description) {}
}
