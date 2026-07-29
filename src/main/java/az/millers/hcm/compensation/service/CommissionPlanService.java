package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.CommissionPlan;
import az.millers.hcm.compensation.domain.CommissionTier;
import az.millers.hcm.compensation.repo.CommissionPlanRepository;
import az.millers.hcm.compensation.repo.CommissionTierRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M366 — Commission Plan service.
 */
@Service
public class CommissionPlanService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "CommissionPlan";

    private final CommissionPlanRepository plans;
    private final CommissionTierRepository tiers;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CommissionPlanService(CommissionPlanRepository plans,
                                 CommissionTierRepository tiers,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.plans = plans;
        this.tiers = tiers;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<CommissionPlan> listActive() {
        return plans.findByTenantIdAndIsActiveTrue(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public List<CommissionPlan> listAll() {
        return plans.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public CommissionPlan get(UUID id) {
        return plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission plan not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CommissionTier> getTiers(UUID planId) {
        return tiers.findByPlanIdOrderBySortOrder(planId);
    }

    @Transactional
    public CommissionPlan create(String code, String name, String basis,
                                 BigDecimal flatRatePct, Boolean tiered, String currency) {
        if (plans.findByTenantIdAndCode(TenantContext.current(), code).isPresent()) {
            throw new BadRequestException("COMMISSION_PLAN_CODE_DUPLICATE");
        }

        CommissionPlan plan = new CommissionPlan();
        plan.setTenantId(TenantContext.current());
        plan.setCode(code);
        plan.setName(name);
        plan.setBasis(basis);
        plan.setFlatRatePct(flatRatePct);
        plan.setTiered(tiered != null ? tiered : false);
        plan.setCurrency(currency != null ? currency : "AZN");

        CommissionPlan saved = plans.save(plan);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, Map.of("code", code, "name", name));
        return saved;
    }

    @Transactional
    public CommissionPlan update(UUID id, String name, String basis, BigDecimal flatRatePct, Boolean tiered) {
        CommissionPlan plan = get(id);
        plan.setName(name);
        plan.setBasis(basis);
        plan.setFlatRatePct(flatRatePct);
        plan.setTiered(tiered);

        CommissionPlan saved = plans.save(plan);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, Map.of("name", name));
        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        CommissionPlan plan = get(id);
        plan.setIsActive(false);
        plans.save(plan);
        audit.record(MODULE, ENTITY, id.toString(), "DEACTIVATE", null, null);
    }

    @Transactional
    public List<CommissionTier> updateTiers(UUID planId, List<CommissionTier> tierList) {
        CommissionPlan plan = get(planId);

        // Delete existing tiers
        tiers.deleteByPlanId(planId);

        // Insert new tiers
        for (CommissionTier tier : tierList) {
            tier.setId(null);
            tier.setTenantId(TenantContext.current());
            tier.setPlanId(planId);
            tiers.save(tier);
        }

        audit.record(MODULE, ENTITY, planId.toString(),
                "UPDATE_TIERS", null, Map.of("tierCount", tierList.size()));
        return getTiers(planId);
    }

    /**
     * Compute commission for a given sales amount.
     * If not tiered: salesAmount × flat_rate_pct / 100.
     * If tiered: marginal tiered calc — apply each tier's rate to the portion of salesAmount
     * within [from, to] and sum.
     */
    public BigDecimal computeCommission(CommissionPlan plan, BigDecimal salesAmount) {
        if (!plan.getTiered()) {
            // Flat rate
            if (plan.getFlatRatePct() == null) {
                throw new BadRequestException("Plan is not tiered but has no flat_rate_pct");
            }
            return salesAmount.multiply(plan.getFlatRatePct())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Tiered: marginal calculation
        List<CommissionTier> tierList = getTiers(plan.getId());
        if (tierList.isEmpty()) {
            throw new BadRequestException("Tiered plan has no tiers defined");
        }

        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal remaining = salesAmount;

        for (CommissionTier tier : tierList) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal tierFrom = tier.getFromAmount();
            BigDecimal tierTo = tier.getToAmount(); // null = open top
            BigDecimal ratePct = tier.getRatePct();

            // Calculate portion in this tier
            BigDecimal tierBase = remaining.min(
                    tierTo != null ? tierTo.subtract(tierFrom) : remaining
            );

            // Only process if salesAmount exceeds tierFrom
            if (salesAmount.compareTo(tierFrom) > 0) {
                BigDecimal applicable = salesAmount.subtract(tierFrom).min(tierBase);
                if (applicable.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal tierCommission = applicable.multiply(ratePct)
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    totalCommission = totalCommission.add(tierCommission);
                    remaining = remaining.subtract(applicable);
                }
            }
        }

        return totalCommission.setScale(2, RoundingMode.HALF_UP);
    }
}
