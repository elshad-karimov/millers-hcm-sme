package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.CommissionPayout;
import az.millers.hcm.compensation.domain.CommissionPlan;
import az.millers.hcm.compensation.repo.CommissionPayoutRepository;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * M366 — Commission Payout service.
 */
@Service
public class CommissionPayoutService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "CommissionPayout";

    private final CommissionPayoutRepository payouts;
    private final CommissionPlanService planService;
    private final PayrollBonusRepository bonusRepository;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CommissionPayoutService(CommissionPayoutRepository payouts,
                                   CommissionPlanService planService,
                                   PayrollBonusRepository bonusRepository,
                                   AccessScopeService accessScope,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.payouts = payouts;
        this.planService = planService;
        this.bonusRepository = bonusRepository;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<CommissionPayout> list(UUID planId, UUID employeeId, String status) {
        if (planId != null) {
            return payouts.findByTenantIdAndPlanIdOrderByCreatedAtDesc(TenantContext.current(), planId);
        }
        if (employeeId != null) {
            // Enforce hierarchy access
            if (!accessScope.isAccessible(employeeId)) {
                throw new BadRequestException("Access denied to employee: " + employeeId);
            }
            return payouts.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TenantContext.current(), employeeId);
        }
        if (status != null) {
            return payouts.findByTenantIdAndStatusOrderByCreatedAtDesc(TenantContext.current(), status);
        }
        return payouts.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public CommissionPayout get(UUID id) {
        return payouts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission payout not found: " + id));
    }

    @Transactional
    public CommissionPayout create(UUID planId, UUID employeeId, String period, BigDecimal salesAmount) {
        // Enforce hierarchy access
        if (!accessScope.isAccessible(employeeId)) {
            throw new BadRequestException("Access denied to employee: " + employeeId);
        }

        CommissionPlan plan = planService.get(planId);

        // Compute commission
        BigDecimal commissionAmount = planService.computeCommission(plan, salesAmount);

        CommissionPayout payout = new CommissionPayout();
        payout.setTenantId(TenantContext.current());
        payout.setPlanId(planId);
        payout.setEmployeeId(employeeId);
        payout.setPeriod(period);
        payout.setSalesAmount(salesAmount);
        payout.setCommissionAmount(commissionAmount);
        payout.setStatus("DRAFT");

        CommissionPayout saved = payouts.save(payout);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null,
                Map.of("planCode", plan.getCode(), "employeeId", employeeId.toString(),
                        "period", period, "salesAmount", salesAmount.toPlainString(),
                        "commissionAmount", commissionAmount.toPlainString()));
        return saved;
    }

    @Transactional
    public CommissionPayout approve(UUID payoutId, String approvedBy) {
        CommissionPayout payout = get(payoutId);

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

        CommissionPayout saved = payouts.save(payout);
        audit.record(MODULE, ENTITY, payoutId.toString(),
                "APPROVED", null,
                Map.of("approvedBy", approvedBy, "commissionAmount", saved.getCommissionAmount().toPlainString()));
        return saved;
    }

    @Transactional
    public void cancel(UUID payoutId) {
        CommissionPayout payout = get(payoutId);

        if (!"DRAFT".equals(payout.getStatus())) {
            throw new BadRequestException("Only DRAFT payouts can be cancelled");
        }

        payout.setStatus("CANCELLED");
        payouts.save(payout);
        audit.record(MODULE, ENTITY, payoutId.toString(), "CANCEL", null, null);
    }
}
