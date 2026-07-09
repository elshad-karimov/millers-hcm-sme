package az.millers.hcm.self.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.corehr.api.dto.AssetResponse;
import az.millers.hcm.corehr.service.EmployeeAssetService;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCaseType;
import az.millers.hcm.lifecycle.service.AssetDamageLossCaseService;
import az.millers.hcm.security.scope.AccessScopeService;

@RestController
@RequestMapping("/api/self/assets")
public class EmployeeSelfAssetController {

    private final EmployeeAssetService assetService;
    private final AssetDamageLossCaseService damageCaseService;
    private final AccessScopeService accessScope;

    public EmployeeSelfAssetController(EmployeeAssetService assetService,
                                       AssetDamageLossCaseService damageCaseService,
                                       AccessScopeService accessScope) {
        this.assetService = assetService;
        this.damageCaseService = damageCaseService;
        this.accessScope = accessScope;
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<AssetResponse> getMyAssets(@PathVariable UUID employeeId) {
        requireAccessible(employeeId);
        return assetService.listFor(employeeId, true);
    }

    @PostMapping("/{employeeId}/report-damage")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public void reportDamage(@PathVariable UUID employeeId, @RequestBody ReportDamageRequest req) {
        requireAccessible(employeeId);
        damageCaseService.report(req.assetId, employeeId, req.caseType, req.description, null);
    }

    private void requireAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new AccessDeniedException("Asset is outside your access scope");
        }
    }

    public record ReportDamageRequest(UUID assetId, AssetDamageLossCaseType caseType, String description) {}
}
