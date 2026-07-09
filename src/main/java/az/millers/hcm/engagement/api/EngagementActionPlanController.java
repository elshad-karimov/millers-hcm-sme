package az.millers.hcm.engagement.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.engagement.domain.EngagementActionItem;
import az.millers.hcm.engagement.domain.EngagementActionPlan;
import az.millers.hcm.engagement.service.EngagementActionPlanService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M479 — Engagement action plan endpoints (HR_ADMIN/HR_SPECIALIST).
 */
@RestController
@RequestMapping("/api/engagement/action-plans")
public class EngagementActionPlanController {

    private final EngagementActionPlanService service;

    public EngagementActionPlanController(EngagementActionPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<EngagementActionPlan> listAll() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.getWithProgress(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EngagementActionPlan create(@RequestBody EngagementActionPlan plan) {
        return service.create(plan);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EngagementActionPlan update(@PathVariable UUID id, @RequestBody EngagementActionPlan plan) {
        return service.update(id, plan);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ── Action items ──

    @GetMapping("/{planId}/items")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<EngagementActionItem> listItems(@PathVariable UUID planId) {
        return service.listItems(planId);
    }

    @PostMapping("/{planId}/items")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EngagementActionItem addItem(@PathVariable UUID planId, @RequestBody EngagementActionItem item) {
        return service.addItem(planId, item);
    }

    @PostMapping("/items/{itemId}/toggle")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EngagementActionItem toggleItem(@PathVariable UUID itemId) {
        return service.toggleItem(itemId);
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void deleteItem(@PathVariable UUID itemId) {
        service.deleteItem(itemId);
    }
}
