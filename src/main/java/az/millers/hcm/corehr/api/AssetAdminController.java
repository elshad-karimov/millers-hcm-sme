package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.AssetEventResponse;
import az.millers.hcm.corehr.api.dto.AssetReissueRequest;
import az.millers.hcm.corehr.api.dto.AssetResponse;
import az.millers.hcm.corehr.api.dto.DepreciationResponse.AssetDepreciation;
import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.AssetType;
import az.millers.hcm.corehr.repo.EmployeeAssetRepository;
import az.millers.hcm.corehr.service.AssetDepreciationService;
import az.millers.hcm.corehr.service.AssetEventService;
import az.millers.hcm.corehr.service.EmployeeAssetService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M124 — asset-centric admin surface.
 *
 * <p>Until M124 assets were only reachable via the employee profile
 * (M72's
 * {@code /api/employees/&lbrace;id&rbrace;/assets} family). HR needed a
 * cross-employee view ("show me every laptop currently checked out",
 * "show me everything I've marked LOST this year") + an event-log
 * read + a one-call reissue. This controller provides those.
 */
@RestController
@RequestMapping("/api/assets")
@PreAuthorize(SecurityRoles.READ_HR)
public class AssetAdminController {

    private final EmployeeAssetRepository repo;
    private final EmployeeAssetService assets;
    private final AssetEventService events;
    /** M128 — depreciation schedule for an asset. */
    private final AssetDepreciationService depreciation;

    public AssetAdminController(EmployeeAssetRepository repo,
                                EmployeeAssetService assets,
                                AssetEventService events,
                                AssetDepreciationService depreciation) {
        this.repo = repo;
        this.assets = assets;
        this.events = events;
        this.depreciation = depreciation;
    }

    /**
     * Cross-employee search. All filters optional. Pagination is
     * deliberately omitted in Phase 1 — the typical org has &lt; 1k
     * assets, so the list fits on one screen with sort + filter.
     */
    @GetMapping
    public List<AssetResponse> list(
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) UUID employeeId) {
        return repo.search(status, type, employeeId).stream()
                .map(AssetResponse::from)
                .toList();
    }

    /** Counts per status for the admin-screen badge row. */
    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return Map.of(
                "ASSIGNED",    repo.countByStatus(AssetStatus.ASSIGNED),
                "RETURNED",    repo.countByStatus(AssetStatus.RETURNED),
                "LOST",        repo.countByStatus(AssetStatus.LOST),
                "DAMAGED",     repo.countByStatus(AssetStatus.DAMAGED),
                "WRITTEN_OFF", repo.countByStatus(AssetStatus.WRITTEN_OFF));
    }

    /** Append-only event timeline for a single asset. */
    @GetMapping("/{id}/events")
    public List<AssetEventResponse> history(@PathVariable UUID id) {
        return events.historyFor(id).stream().map(AssetEventResponse::from).toList();
    }

    /** Atomic close-and-reissue to a different employee. */
    @PostMapping("/{id}/reissue")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AssetResponse reissue(@PathVariable UUID id,
                                  @RequestBody AssetReissueRequest req) {
        return assets.reissue(id, req.newEmployeeId(), req.effectiveAt(),
                req.conditionAtReturn(), req.conditionAtAssignment(), req.notes());
    }

    /** M128 — month-by-month depreciation schedule for an asset. */
    @GetMapping("/{id}/depreciation")
    public AssetDepreciation depreciation(@PathVariable UUID id) {
        return depreciation.scheduleFor(id);
    }
}
