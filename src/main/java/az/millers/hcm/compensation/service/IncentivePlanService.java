package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.IncentivePlan;
import az.millers.hcm.compensation.repo.IncentivePlanRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M365 — Incentive Plan service.
 */
@Service
public class IncentivePlanService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "IncentivePlan";

    private final IncentivePlanRepository plans;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public IncentivePlanService(IncentivePlanRepository plans,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.plans = plans;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<IncentivePlan> listActive() {
        return plans.findByTenantIdAndIsActiveTrue(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public List<IncentivePlan> listAll() {
        return plans.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public IncentivePlan get(UUID id) {
        return plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incentive plan not found: " + id));
    }

    @Transactional
    public IncentivePlan create(String code, String name, String measure,
                                BigDecimal targetPct, BigDecimal thresholdAchievement,
                                BigDecimal targetAchievement, BigDecimal capAchievement,
                                BigDecimal maxPayoutPct, String currency) {
        if (plans.findByTenantIdAndCode(TenantContext.current(), code).isPresent()) {
            throw new BadRequestException("INCENTIVE_PLAN_CODE_DUPLICATE");
        }

        IncentivePlan plan = new IncentivePlan();
        plan.setTenantId(TenantContext.current());
        plan.setCode(code);
        plan.setName(name);
        plan.setMeasure(measure);
        plan.setTargetPct(targetPct);
        plan.setThresholdAchievement(thresholdAchievement != null ? thresholdAchievement : new BigDecimal("80"));
        plan.setTargetAchievement(targetAchievement != null ? targetAchievement : new BigDecimal("100"));
        plan.setCapAchievement(capAchievement != null ? capAchievement : new BigDecimal("120"));
        plan.setMaxPayoutPct(maxPayoutPct != null ? maxPayoutPct
                : targetPct.multiply(new BigDecimal("1.5")).setScale(2, RoundingMode.HALF_UP));
        plan.setCurrency(currency != null ? currency : "AZN");

        IncentivePlan saved = plans.save(plan);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, Map.of("code", code, "name", name));
        return saved;
    }

    @Transactional
    public IncentivePlan update(UUID id, String name, String measure,
                                BigDecimal targetPct, BigDecimal thresholdAchievement,
                                BigDecimal targetAchievement, BigDecimal capAchievement,
                                BigDecimal maxPayoutPct) {
        IncentivePlan plan = get(id);
        plan.setName(name);
        plan.setMeasure(measure);
        plan.setTargetPct(targetPct);
        plan.setThresholdAchievement(thresholdAchievement);
        plan.setTargetAchievement(targetAchievement);
        plan.setCapAchievement(capAchievement);
        plan.setMaxPayoutPct(maxPayoutPct);

        IncentivePlan saved = plans.save(plan);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, Map.of("name", name));
        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        IncentivePlan plan = get(id);
        plan.setIsActive(false);
        plans.save(plan);
        audit.record(MODULE, ENTITY, id.toString(), "DEACTIVATE", null, null);
    }

    /**
     * Compute payout % and amount for a given eligible salary and achievement %.
     * Curve: achievement < threshold → 0; threshold-target → linear 0 to target_pct;
     * target-cap → linear target_pct to max_payout_pct; > cap → capped.
     */
    public Map<String, BigDecimal> computePayout(IncentivePlan plan, BigDecimal eligibleSalary,
                                                  BigDecimal achievementPct) {
        BigDecimal threshold = plan.getThresholdAchievement();
        BigDecimal target = plan.getTargetAchievement();
        BigDecimal cap = plan.getCapAchievement();
        BigDecimal targetPct = plan.getTargetPct();
        BigDecimal maxPayoutPct = plan.getMaxPayoutPct();

        BigDecimal payoutPct;

        if (achievementPct.compareTo(threshold) < 0) {
            // Below threshold: 0
            payoutPct = BigDecimal.ZERO;
        } else if (achievementPct.compareTo(target) <= 0) {
            // Threshold to target: linear ramp 0 to target_pct
            BigDecimal range = target.subtract(threshold);
            BigDecimal progress = achievementPct.subtract(threshold);
            payoutPct = targetPct.multiply(progress)
                    .divide(range, 4, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);
        } else if (achievementPct.compareTo(cap) <= 0) {
            // Target to cap: linear ramp target_pct to max_payout_pct
            BigDecimal range = cap.subtract(target);
            BigDecimal progress = achievementPct.subtract(target);
            BigDecimal extraPct = maxPayoutPct.subtract(targetPct);
            payoutPct = targetPct.add(
                    extraPct.multiply(progress)
                            .divide(range, 4, RoundingMode.HALF_UP)
            ).setScale(2, RoundingMode.HALF_UP);
        } else {
            // Above cap: capped at max_payout_pct
            payoutPct = maxPayoutPct;
        }

        BigDecimal payoutAmount = eligibleSalary.multiply(payoutPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return Map.of(
                "payoutPct", payoutPct,
                "payoutAmount", payoutAmount
        );
    }
}
