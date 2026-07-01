package az.millers.hcm.compensation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.IncentivePayout;
import az.millers.hcm.compensation.domain.IncentivePlan;
import az.millers.hcm.compensation.repo.IncentivePayoutRepository;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * M365 — Incentive Payout service.
 */
@Service
public class IncentivePayoutService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "IncentivePayout";
    private static final String TENANT = "default";

    private final IncentivePayoutRepository payouts;
    private final IncentivePlanService planService;
    private final CompensationService compensationService;
    private final PayrollBonusRepository bonusRepository;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public IncentivePayoutService(IncentivePayoutRepository payouts,
                                   IncentivePlanService planService,
                                   CompensationService compensationService,
                                   PayrollBonusRepository bonusRepository,
                                   AccessScopeService accessScope,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.payouts = payouts;
        this.planService = planService;
        this.compensationService = compensationService;
        this.bonusRepository = bonusRepository;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<IncentivePayout> list(UUID planId, UUID employeeId, String status) {
        if (planId != null) {
            return payouts.findByTenantIdAndPlanIdOrderByCreatedAtDesc(TENANT, planId);
        }
        if (employeeId != null) {
            // Enforce hierarchy access
            if (!accessScope.isAccessible(employeeId)) {
                throw new BadRequestException("Access denied to employee: " + employeeId);
            }
            return payouts.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        }
        if (status != null) {
            return payouts.findByTenantIdAndStatusOrderByCreatedAtDesc(TENANT, status);
        }
        return payouts.findByTenantIdOrderByCreatedAtDesc(TENANT);
    }

    @Transactional(readOnly = true)
    public IncentivePayout get(UUID id) {
        return payouts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incentive payout not found: " + id));
    }

    @Transactional
    public IncentivePayout create(UUID planId, UUID employeeId, String period, BigDecimal achievementPct) {
        // Enforce hierarchy access
        if (!accessScope.isAccessible(employeeId)) {
            throw new BadRequestException("Access denied to employee: " + employeeId);
        }

        IncentivePlan plan = planService.get(planId);

        // Resolve eligible salary (current base salary)
        BigDecimal eligibleSalary;
        try {
            eligibleSalary = compensationService.getActiveOn(employeeId, LocalDate.now()).getMonthlyBaseSalary();
        } catch (Exception e) {
            throw new BadRequestException("Employee has no active compensation record");
        }

        // Compute payout
        Map<String, BigDecimal> result = planService.computePayout(plan, eligibleSalary, achievementPct);
        BigDecimal payoutPct = result.get("payoutPct");
        BigDecimal payoutAmount = result.get("payoutAmount");

        IncentivePayout payout = new IncentivePayout();
        payout.setTenantId(TENANT);
        payout.setPlanId(planId);
        payout.setEmployeeId(employeeId);
        payout.setPeriod(period);
        payout.setEligibleSalary(eligibleSalary);
        payout.setAchievementPct(achievementPct);
        payout.setPayoutPct(payoutPct);
        payout.setPayoutAmount(payoutAmount);
        payout.setStatus("DRAFT");

        IncentivePayout saved = payouts.save(payout);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null,
                Map.of("planCode", plan.getCode(), "employeeId", employeeId.toString(),
                        "period", period, "payoutAmount", payoutAmount.toPlainString()));
        return saved;
    }

    @Transactional
    public IncentivePayout approve(UUID payoutId, String approvedBy) {
        IncentivePayout payout = get(payoutId);

        if (!"DRAFT".equals(payout.getStatus())) {
            throw new BadRequestException("Only DRAFT payouts can be approved (was " + payout.getStatus() + ")");
        }

        // Approval only marks the payout ready; it is NOT attached to payroll here.
        // A payroll_bonus row requires a target run (payroll_bonus.run_id is NOT NULL), so
        // the controlled comp -> payroll transfer (M369) resolves the target run and pushes
        // approved one-time comp (incentive/commission) to it idempotently.
        payout.setStatus("APPROVED");
        payout.setApprovedBy(approvedBy);
        payout.setApprovedAt(OffsetDateTime.now());

        IncentivePayout saved = payouts.save(payout);
        audit.record(MODULE, ENTITY, payoutId.toString(),
                "APPROVED", null,
                Map.of("approvedBy", approvedBy, "payoutAmount", saved.getPayoutAmount().toPlainString()));
        return saved;
    }

    @Transactional
    public void cancel(UUID payoutId) {
        IncentivePayout payout = get(payoutId);

        if (!"DRAFT".equals(payout.getStatus())) {
            throw new BadRequestException("Only DRAFT payouts can be cancelled");
        }

        payout.setStatus("CANCELLED");
        payouts.save(payout);
        audit.record(MODULE, ENTITY, payoutId.toString(), "CANCEL", null, null);
    }
}
