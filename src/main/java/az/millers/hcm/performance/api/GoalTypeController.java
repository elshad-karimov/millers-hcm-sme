package az.millers.hcm.performance.api;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.domain.GoalType;
import az.millers.hcm.performance.repo.GoalTypeRepository;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_13 M403 — business goal-type catalog (PRD 13 §4). A thin CRUD kept in the
 * controller: 14 seeded types, HR-admin maintained, read by every goal form.
 */
@RestController
@RequestMapping("/api/performance/goal-types")
public class GoalTypeController {

    private static final List<String> CATEGORIES =
            List.of("COMPANY", "DEPARTMENT", "TEAM", "INDIVIDUAL", "DEVELOPMENT");

    public record GoalTypeRequest(String code, String name, String description,
                                  String defaultCategory, Integer sortOrder, Boolean active) {}

    private final GoalTypeRepository types;
    private final AuditService audit;

    public GoalTypeController(GoalTypeRepository types, AuditService audit) {
        this.types = types;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<GoalType> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return activeOnly
                ? types.findByTenantIdAndActiveTrueOrderBySortOrderAsc(TenantContext.current())
                : types.findByTenantIdOrderBySortOrderAsc(TenantContext.current());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public GoalType create(@RequestBody GoalTypeRequest req) {
        validate(req);
        String code = req.code().trim().toUpperCase();
        if (types.existsByTenantIdAndCode(TenantContext.current(), code)) {
            throw new BadRequestException("Goal type code already exists: " + code);
        }
        GoalType t = new GoalType();
        t.setTenantId(TenantContext.current());
        t.setCode(code);
        apply(t, req);
        GoalType saved = types.save(t);
        audit.record("PERFORMANCE", "GoalType", saved.getId().toString(), "CREATE", null, code);
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public GoalType update(@PathVariable UUID id, @RequestBody GoalTypeRequest req) {
        validate(req);
        GoalType t = types.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal type not found: " + id));
        apply(t, req);
        GoalType saved = types.save(t);
        audit.record("PERFORMANCE", "GoalType", id.toString(), "UPDATE", null, t.getCode());
        return saved;
    }

    private static void validate(GoalTypeRequest req) {
        if (req.code() == null || req.code().isBlank()
                || req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("Goal type code and name are required");
        }
        if (req.defaultCategory() != null && !CATEGORIES.contains(req.defaultCategory())) {
            throw new BadRequestException("defaultCategory must be one of " + CATEGORIES);
        }
    }

    private static void apply(GoalType t, GoalTypeRequest req) {
        t.setName(req.name().trim());
        t.setDescription(req.description());
        if (req.defaultCategory() != null) t.setDefaultCategory(req.defaultCategory());
        if (req.sortOrder() != null) t.setSortOrder(req.sortOrder());
        if (req.active() != null) t.setActive(req.active());
    }
}
