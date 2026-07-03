package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BenefitCoverageTier;
import az.millers.hcm.compbenefits.domain.BenefitEligibilityRule;
import az.millers.hcm.compbenefits.domain.BenefitPlanTier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** DTOs for plan coverage tiers + eligibility rules (HCM_11 M375). */
public final class BenefitPlanConfigDtos {

    private BenefitPlanConfigDtos() {}

    // ── Coverage tiers ───────────────────────────────────────────────────────

    public record TierRequest(
            @NotNull BenefitCoverageTier tierCode,
            String tierLabel,
            @PositiveOrZero BigDecimal employerContribution,
            @PositiveOrZero BigDecimal employeeContribution,
            @PositiveOrZero BigDecimal coverageAmount,
            Integer displayOrder,
            Boolean active) {
    }

    public record TierResponse(
            UUID id,
            UUID planId,
            BenefitCoverageTier tierCode,
            String tierLabel,
            BigDecimal employerContribution,
            BigDecimal employeeContribution,
            BigDecimal coverageAmount,
            int displayOrder,
            boolean active) {

        public static TierResponse from(BenefitPlanTier t) {
            return new TierResponse(
                    t.getId(), t.getPlanId(), t.getTierCode(), t.getTierLabel(),
                    t.getEmployerContribution(), t.getEmployeeContribution(), t.getCoverageAmount(),
                    t.getDisplayOrder(), t.isActive());
        }
    }

    /** Full-replace payload for a plan's tiers. */
    public record TiersReplaceRequest(@NotNull List<TierRequest> tiers) {}

    // ── Eligibility rules ────────────────────────────────────────────────────

    public record EligibilityRuleRequest(
            String employmentType,
            UUID departmentId,
            UUID orgUnitId,
            UUID gradeId,
            String employeeCategory,
            @PositiveOrZero Integer minServiceMonths,
            String description,
            Boolean active) {
    }

    public record EligibilityRuleResponse(
            UUID id,
            UUID planId,
            String employmentType,
            UUID departmentId,
            UUID orgUnitId,
            UUID gradeId,
            String employeeCategory,
            Integer minServiceMonths,
            String description,
            boolean active) {

        public static EligibilityRuleResponse from(BenefitEligibilityRule r) {
            return new EligibilityRuleResponse(
                    r.getId(), r.getPlanId(), r.getEmploymentType(), r.getDepartmentId(),
                    r.getOrgUnitId(), r.getGradeId(), r.getEmployeeCategory(),
                    r.getMinServiceMonths(), r.getDescription(), r.isActive());
        }
    }

    /** Full-replace payload for a plan's eligibility rules. */
    public record EligibilityReplaceRequest(@NotNull List<EligibilityRuleRequest> rules) {}
}
