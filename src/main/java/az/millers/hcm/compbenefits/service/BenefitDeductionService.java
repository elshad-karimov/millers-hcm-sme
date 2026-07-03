package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.repo.PayrollDeductionRepository;

/**
 * HCM_11 M378 — bridges a benefit enrollment's employee contribution to payroll as a
 * RECURRING {@link PayrollDeduction} (which the PayrollEngine already picks up each period).
 *
 * <p><strong>Payroll-impacting:</strong> the created deduction is subtracted from taxable
 * gross by the engine, i.e. benefit employee-contributions are treated PRE-TAX. Idempotent:
 * keyed on {@code source_benefit_enrollment_id} so re-activation never double-deducts.
 */
@Service
public class BenefitDeductionService {

    private static final Logger log = LoggerFactory.getLogger(BenefitDeductionService.class);
    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "PayrollDeduction";
    private static final String RECURRING = "RECURRING";
    private static final String ACTIVE = "ACTIVE";
    private static final String CANCELLED = "CANCELLED";

    private final PayrollDeductionRepository deductions;
    private final BenefitPlanRepository plans;
    private final AuditService audit;

    public BenefitDeductionService(PayrollDeductionRepository deductions,
                                   BenefitPlanRepository plans,
                                   AuditService audit) {
        this.deductions = deductions;
        this.plans = plans;
        this.audit = audit;
    }

    /**
     * Ensure a RECURRING payroll deduction exists for this enrollment's employee contribution.
     * No-op when the contribution is zero or an ACTIVE deduction already exists (idempotent).
     */
    @Transactional
    public void ensureDeduction(BenefitEnrollment e) {
        BigDecimal amount = e.getEmployeeContribution();
        if (amount == null || amount.signum() <= 0) {
            return; // no employee contribution → nothing to deduct
        }
        if (deductions.findFirstBySourceBenefitEnrollmentIdAndStatus(e.getId(), ACTIVE).isPresent()) {
            return; // already bridged
        }
        BenefitPlan plan = plans.findById(e.getPlanId()).orElse(null);
        String planName = plan == null ? "benefit plan" : plan.getName();

        PayrollDeduction d = new PayrollDeduction();
        d.setTenantId(e.getTenantId());
        d.setEmployeeId(e.getEmployeeId());
        d.setDeductionType(RECURRING);
        d.setDescription("Benefit contribution: " + planName);
        d.setAmountPerPeriod(amount);
        d.setStartPeriodYear(e.getStartDate().getYear());
        d.setStartPeriodMonth(e.getStartDate().getMonthValue());
        // Open-ended — recurs until the enrollment is suspended / terminated.
        d.setStatus(ACTIVE);
        d.setSourceBenefitEnrollmentId(e.getId());
        d.setCreatedBy("system:benefits");
        d.setNote("Auto-created from benefit enrollment " + e.getId());
        PayrollDeduction saved = deductions.save(d);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE_FROM_ENROLLMENT",
                null, e.getId().toString());
        log.info("Created recurring benefit deduction {} ({} /mo) for enrollment {}",
                saved.getId(), amount, e.getId());
    }

    /** Cancel the ACTIVE recurring deduction for this enrollment, if present. Idempotent. */
    @Transactional
    public void cancelDeduction(BenefitEnrollment e) {
        deductions.findFirstBySourceBenefitEnrollmentIdAndStatus(e.getId(), ACTIVE).ifPresent(d -> {
            d.setStatus(CANCELLED);
            deductions.save(d);
            audit.record(MODULE, ENTITY, d.getId().toString(), "CANCEL_FROM_ENROLLMENT",
                    null, e.getId().toString());
            log.info("Cancelled benefit deduction {} for enrollment {}", d.getId(), e.getId());
        });
    }
}
