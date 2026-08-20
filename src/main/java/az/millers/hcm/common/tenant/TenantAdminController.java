package az.millers.hcm.common.tenant;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.config.plan.Plan;
import az.millers.hcm.config.plan.TenantPlanService;
import jakarta.validation.constraints.NotBlank;

/**
 * Tenant administration (multi-tenancy Phase 4). SYSTEM_ADMIN only — this is a
 * cross-tenant control-plane surface, not a per-tenant business API.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class TenantAdminController {

    private final TenantRepository tenants;
    private final TenantProvisioningService provisioning;
    private final TenantPlanService planService;
    private final AuditService audit;
    private final az.millers.hcm.security.CurrentRequest currentRequest;

    public TenantAdminController(TenantRepository tenants, TenantProvisioningService provisioning,
                                 TenantPlanService planService, AuditService audit,
                                 az.millers.hcm.security.CurrentRequest currentRequest) {
        this.tenants = tenants;
        this.provisioning = provisioning;
        this.planService = planService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    public record TenantView(String id, String name, String issuerUri, String realm,
                             boolean active, String plan) {
        static TenantView of(Tenant t) {
            return new TenantView(t.getId(), t.getName(), t.getIssuerUri(), t.getRealm(),
                    t.isActive(), t.getPlan() != null ? t.getPlan().name() : null);
        }
    }

    public record ProvisionRequest(
            @NotBlank String id,
            @NotBlank String name,
            @NotBlank String issuerUri,
            String realm,
            String seedFromTenant,
            /** LITE | STANDARD | ENTERPRISE; omitted → the edition default (LITE). */
            String plan) {}

    public record PlanChangeRequest(@NotBlank String plan) {}

    public record PlanChangeResult(String tenantId, String previousPlan, String plan) {}

    @GetMapping
    public List<TenantView> list() {
        return tenants.findAll().stream().map(TenantView::of).toList();
    }

    @PostMapping
    public TenantProvisioningService.ProvisionResult provision(@RequestBody ProvisionRequest req) {
        String actor = currentRequest.username();
        return provisioning.provision(req.id(), req.name(), req.issuerUri(),
                req.realm(), req.seedFromTenant(), parsePlan(req.plan()), actor);
    }

    /**
     * Move a tenant between editions.
     *
     * <p>A downgrade is non-destructive — modules stop answering, no rows are
     * removed — so it is reversible by upgrading again. Audit-logged because it
     * changes what an entire tenant can see and do.
     */
    @PutMapping("/{tenantId}/plan")
    public PlanChangeResult changePlan(@PathVariable String tenantId,
                                       @RequestBody PlanChangeRequest req) {
        Plan target = strictPlan(req.plan());
        if (!tenants.existsById(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant: " + tenantId);
        }
        Plan previous = planService.changePlan(tenantId, target, currentRequest.username());
        audit.record("PLATFORM", "Tenant", tenantId, "PLAN_CHANGE",
                Map.of("plan", previous.name()), Map.of("plan", target.name()));
        return new PlanChangeResult(tenantId, previous.name(), target.name());
    }

    /** Provisioning is lenient: an absent plan means "the edition default". */
    private static Plan parsePlan(String raw) {
        return raw == null || raw.isBlank() ? Plan.defaultPlan() : strictPlan(raw);
    }

    /**
     * A plan CHANGE is strict: silently coercing a typo to LITE would downgrade
     * a paying tenant.
     */
    private static Plan strictPlan(String raw) {
        try {
            return Plan.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown plan '" + raw + "'. Expected one of LITE, STANDARD, ENTERPRISE.");
        }
    }
}
