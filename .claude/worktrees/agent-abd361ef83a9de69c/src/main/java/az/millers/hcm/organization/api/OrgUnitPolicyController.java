package az.millers.hcm.organization.api;

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

import az.millers.hcm.organization.domain.OrgUnitHistory;
import az.millers.hcm.organization.domain.OrgUnitPolicy;
import az.millers.hcm.organization.service.OrgUnitHistoryService;
import az.millers.hcm.organization.service.OrgUnitPolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Per-unit policy CRUD + per-unit history (M81). Mounted on the existing
 * {@code /api/org} URL family so the Org page can reach both without new
 * top-level routes.
 */
@RestController
@RequestMapping("/api/org")
public class OrgUnitPolicyController {

    /** Centralised role sets — see {@link az.millers.hcm.security.SecurityRoles}. */
    private static final String READ_ROLES = az.millers.hcm.security.SecurityRoles.READ_HR_PLUS_MANAGERS;
    private static final String WRITE_ROLES = az.millers.hcm.security.SecurityRoles.WRITE_HR;

    private final OrgUnitPolicyService policies;
    private final OrgUnitHistoryService history;

    public OrgUnitPolicyController(OrgUnitPolicyService policies,
                                    OrgUnitHistoryService history) {
        this.policies = policies;
        this.history = history;
    }

    /** Returns null when no override exists — the SPA reads that as "inherits". */
    @GetMapping("/units/{unitId}/policy/{versionId}")
    @PreAuthorize(READ_ROLES)
    public OrgUnitPolicy get(@PathVariable UUID unitId, @PathVariable UUID versionId) {
        return policies.get(unitId, versionId);
    }

    @PutMapping("/units/{unitId}/policy/{versionId}")
    @PreAuthorize(WRITE_ROLES)
    public OrgUnitPolicy upsert(@PathVariable UUID unitId,
                                 @PathVariable UUID versionId,
                                 @Valid @RequestBody PolicyUpsertRequest req) {
        return policies.upsert(unitId, versionId,
                req.leaveGroupId(), req.payrollGroupId(), req.notes());
    }

    @DeleteMapping("/units/{unitId}/policy/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void delete(@PathVariable UUID unitId, @PathVariable UUID versionId) {
        policies.delete(unitId, versionId);
    }

    @GetMapping("/units/{unitId}/history")
    @PreAuthorize(READ_ROLES)
    public List<OrgUnitHistory> historyOf(@PathVariable UUID unitId) {
        return history.historyOf(unitId);
    }

    @GetMapping("/versions/{versionId}/recent-history")
    @PreAuthorize(READ_ROLES)
    public List<OrgUnitHistory> recentInVersion(@PathVariable UUID versionId) {
        return history.recentInVersion(versionId);
    }

    public record PolicyUpsertRequest(
            UUID leaveGroupId,
            UUID payrollGroupId,
            @Size(max = 4000) String notes) {}
}
