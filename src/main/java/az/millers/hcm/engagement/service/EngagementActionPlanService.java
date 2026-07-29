package az.millers.hcm.engagement.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.engagement.domain.EngagementActionItem;
import az.millers.hcm.engagement.domain.EngagementActionPlan;
import az.millers.hcm.engagement.repo.EngagementActionItemRepository;
import az.millers.hcm.engagement.repo.EngagementActionPlanRepository;

/**
 * M479 — Engagement action plan service.
 */
@Service
public class EngagementActionPlanService {


    private final EngagementActionPlanRepository planRepo;
    private final EngagementActionItemRepository itemRepo;

    public EngagementActionPlanService(EngagementActionPlanRepository planRepo,
                                      EngagementActionItemRepository itemRepo) {
        this.planRepo = planRepo;
        this.itemRepo = itemRepo;
    }

    @Transactional(readOnly = true)
    public List<EngagementActionPlan> listAll() {
        return planRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public EngagementActionPlan get(UUID id) {
        return planRepo.findById(id)
                .filter(p -> TenantContext.current().equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Action plan not found"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWithProgress(UUID id) {
        EngagementActionPlan plan = get(id);
        List<EngagementActionItem> items = itemRepo.findByPlanIdOrderByCreatedAtAsc(id);

        long total = items.size();
        long done = items.stream().filter(EngagementActionItem::getDone).count();

        return Map.of(
                "plan", plan,
                "items", items,
                "progress", Map.of("done", done, "total", total)
        );
    }

    @Transactional
    public EngagementActionPlan create(EngagementActionPlan plan) {
        plan.setTenantId(TenantContext.current());
        return planRepo.save(plan);
    }

    @Transactional
    public EngagementActionPlan update(UUID id, EngagementActionPlan updates) {
        EngagementActionPlan existing = get(id);
        existing.setCampaignId(updates.getCampaignId());
        existing.setOrgUnitId(updates.getOrgUnitId());
        existing.setOwnerUsername(updates.getOwnerUsername());
        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setStatus(updates.getStatus());
        existing.setDueDate(updates.getDueDate());
        return planRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        EngagementActionPlan plan = get(id);
        planRepo.delete(plan);
    }

    // ── Action items ──

    @Transactional(readOnly = true)
    public List<EngagementActionItem> listItems(UUID planId) {
        get(planId); // Verify plan exists and tenant
        return itemRepo.findByPlanIdOrderByCreatedAtAsc(planId);
    }

    @Transactional
    public EngagementActionItem addItem(UUID planId, EngagementActionItem item) {
        get(planId); // Verify plan exists
        item.setTenantId(TenantContext.current());
        item.setPlanId(planId);
        return itemRepo.save(item);
    }

    @Transactional
    public EngagementActionItem toggleItem(UUID itemId) {
        EngagementActionItem item = itemRepo.findById(itemId)
                .filter(i -> TenantContext.current().equals(i.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found"));

        item.setDone(!item.getDone());
        item.setDoneAt(item.getDone() ? OffsetDateTime.now() : null);
        return itemRepo.save(item);
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        EngagementActionItem item = itemRepo.findById(itemId)
                .filter(i -> TenantContext.current().equals(i.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found"));
        itemRepo.delete(item);
    }
}
