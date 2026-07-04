package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.FundingStatus;
import az.millers.hcm.staffing.domain.PositionBudget;
import az.millers.hcm.staffing.domain.PositionFunding;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * M244 — DTOs for position budget + funding REST surfaces.
 * One umbrella class so the request / response records for both sit
 * together; the SPA has a single import line on its side too.
 */
public final class PositionBudgetDtos {

    private PositionBudgetDtos() {}

    // ── Budget ──────────────────────────────────────────────────────────

    /** Create / update body for /api/positions/{id}/budgets. */
    public record BudgetRequest(
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @PositiveOrZero BigDecimal budgetedBasicSalary,
            @PositiveOrZero BigDecimal budgetedAllowances,
            @PositiveOrZero BigDecimal budgetedEmployerTax,
            @PositiveOrZero BigDecimal budgetedBonus,
            @PositiveOrZero BigDecimal budgetedOvertime,
            @PositiveOrZero BigDecimal budgetedBenefits,
            @Size(min = 3, max = 3) String currency,
            @Size(max = 120) String budgetOwner,
            @Size(max = 2000) String notes) {

        public PositionBudget toEntity() {
            PositionBudget b = new PositionBudget();
            b.setEffectiveFrom(effectiveFrom);
            b.setEffectiveTo(effectiveTo);
            if (budgetedBasicSalary != null) b.setBudgetedBasicSalary(budgetedBasicSalary);
            if (budgetedAllowances   != null) b.setBudgetedAllowances(budgetedAllowances);
            if (budgetedEmployerTax  != null) b.setBudgetedEmployerTax(budgetedEmployerTax);
            if (budgetedBonus        != null) b.setBudgetedBonus(budgetedBonus);
            if (budgetedOvertime     != null) b.setBudgetedOvertime(budgetedOvertime);
            if (budgetedBenefits     != null) b.setBudgetedBenefits(budgetedBenefits);
            if (currency             != null) b.setCurrency(currency);
            b.setBudgetOwner(budgetOwner);
            b.setNotes(notes);
            return b;
        }
    }

    public record BudgetResponse(
            UUID id,
            UUID positionId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal budgetedBasicSalary,
            BigDecimal budgetedAllowances,
            BigDecimal budgetedEmployerTax,
            BigDecimal budgetedBonus,
            BigDecimal budgetedOvertime,
            BigDecimal budgetedBenefits,
            BigDecimal totalMonthly,
            BigDecimal totalAnnual,
            String currency,
            String budgetOwner,
            String notes,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static BudgetResponse from(PositionBudget b) {
            return new BudgetResponse(
                    b.getId(), b.getPositionId(),
                    b.getEffectiveFrom(), b.getEffectiveTo(),
                    b.getBudgetedBasicSalary(), b.getBudgetedAllowances(),
                    b.getBudgetedEmployerTax(), b.getBudgetedBonus(),
                    b.getBudgetedOvertime(), b.getBudgetedBenefits(),
                    b.totalMonthly(), b.totalAnnual(),
                    b.getCurrency(), b.getBudgetOwner(), b.getNotes(),
                    b.getCreatedAt(), b.getCreatedBy(),
                    b.getUpdatedAt(), b.getUpdatedBy());
        }
    }

    // ── Funding ─────────────────────────────────────────────────────────

    public record FundingRequest(
            @NotNull FundingStatus status,
            @Size(max = 160) String fundingSource,
            @Size(max = 120) String fundingOwner,
            LocalDate fundingExpiry,
            @Size(max = 2000) String notes) {

        public PositionFunding toEntity() {
            PositionFunding f = new PositionFunding();
            f.setStatus(status);
            f.setFundingSource(fundingSource);
            f.setFundingOwner(fundingOwner);
            f.setFundingExpiry(fundingExpiry);
            f.setNotes(notes);
            return f;
        }
    }

    public record FundingResponse(
            UUID positionId,
            FundingStatus status,
            String fundingSource,
            String fundingOwner,
            LocalDate fundingExpiry,
            String notes,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static FundingResponse from(PositionFunding f) {
            return new FundingResponse(
                    f.getPositionId(), f.getStatus(),
                    f.getFundingSource(), f.getFundingOwner(), f.getFundingExpiry(),
                    f.getNotes(),
                    f.getUpdatedAt(), f.getUpdatedBy());
        }
    }
}
