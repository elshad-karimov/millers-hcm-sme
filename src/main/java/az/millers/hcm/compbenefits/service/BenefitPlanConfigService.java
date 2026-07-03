package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.EligibilityRuleRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.EligibilityRuleResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.TierRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.TierResponse;
import az.millers.hcm.compbenefits.domain.BenefitEligibilityRule;
import az.millers.hcm.compbenefits.domain.BenefitPlanTier;
import az.millers.hcm.compbenefits.repo.BenefitEligibilityRuleRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanTierRepository;

/**
 * HCM_11 M375 — manages a plan's coverage tiers and eligibility rules. Both are
 * managed with full-replace semantics (mirrors the checklist-template task editor).
 * Also exposes the reusable eligibility matcher used by enrolment (M376).
 */
@Service
public class BenefitPlanConfigService {

    private static final String MODULE = "COMP_BENEFITS";

    private final BenefitPlanRepository plans;
    private final BenefitPlanTierRepository tiers;
    private final BenefitEligibilityRuleRepository rules;
    private final AuditService audit;

    public BenefitPlanConfigService(BenefitPlanRepository plans,
                                    BenefitPlanTierRepository tiers,
                                    BenefitEligibilityRuleRepository rules,
                                    AuditService audit) {
        this.plans = plans;
        this.tiers = tiers;
        this.rules = rules;
        this.audit = audit;
    }

    private void requirePlan(UUID planId) {
        if (!plans.existsById(planId)) {
            throw new ResourceNotFoundException("Benefit plan not found: " + planId);
        }
    }

    // ── Coverage tiers ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TierResponse> listTiers(UUID planId) {
        return tiers.findByPlanIdOrderByDisplayOrderAsc(planId).stream()
                .map(TierResponse::from).toList();
    }

    @Transactional
    public List<TierResponse> replaceTiers(UUID planId, List<TierRequest> reqs) {
        requirePlan(planId);
        // Validate no duplicate tier codes in the payload.
        long distinct = reqs.stream().map(TierRequest::tierCode).distinct().count();
        if (distinct != reqs.size()) {
            throw new BadRequestException("Duplicate tier codes in payload");
        }
        tiers.deleteByPlanId(planId);
        tiers.flush();
        int order = 0;
        for (TierRequest r : reqs) {
            BenefitPlanTier t = new BenefitPlanTier();
            t.setPlanId(planId);
            t.setTierCode(r.tierCode());
            t.setTierLabel(r.tierLabel());
            t.setEmployerContribution(nz(r.employerContribution()));
            t.setEmployeeContribution(nz(r.employeeContribution()));
            t.setCoverageAmount(r.coverageAmount());
            t.setDisplayOrder(r.displayOrder() == null ? order : r.displayOrder());
            t.setActive(r.active() == null ? true : r.active());
            tiers.save(t);
            order++;
        }
        audit.record(MODULE, "BenefitPlanTier", planId.toString(), "REPLACE_TIERS", null, reqs.size());
        return listTiers(planId);
    }

    // ── Eligibility rules ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EligibilityRuleResponse> listRules(UUID planId) {
        return rules.findByPlanIdOrderByCreatedAtAsc(planId).stream()
                .map(EligibilityRuleResponse::from).toList();
    }

    @Transactional
    public List<EligibilityRuleResponse> replaceRules(UUID planId, List<EligibilityRuleRequest> reqs) {
        requirePlan(planId);
        rules.deleteByPlanId(planId);
        rules.flush();
        for (EligibilityRuleRequest r : reqs) {
            BenefitEligibilityRule e = new BenefitEligibilityRule();
            e.setPlanId(planId);
            e.setEmploymentType(blankToNull(r.employmentType()));
            e.setDepartmentId(r.departmentId());
            e.setOrgUnitId(r.orgUnitId());
            e.setGradeId(r.gradeId());
            e.setEmployeeCategory(blankToNull(r.employeeCategory()));
            e.setMinServiceMonths(r.minServiceMonths());
            e.setDescription(blankToNull(r.description()));
            e.setActive(r.active() == null ? true : r.active());
            rules.save(e);
        }
        audit.record(MODULE, "BenefitEligibilityRule", planId.toString(), "REPLACE_RULES", null, reqs.size());
        return listRules(planId);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
