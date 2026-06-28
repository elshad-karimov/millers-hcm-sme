package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeaveTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        String description,
        Boolean paid,
        Boolean requiresAttachment,
        Boolean requiresReplacement,
        @DecimalMin("0.0") BigDecimal defaultAnnualEntitlementDays,
        @DecimalMin("0.0") BigDecimal carryForwardLimitDays,
        @Min(1) Integer maxConsecutiveDays,
        Boolean excludeWeekends,
        Boolean excludeHolidays,
        Boolean active,
        /** Monthly accrual gate flag (PRD 8.5.2 — milestone 34). */
        Boolean accruesMonthly,
        /** Explicit per-month bump; falls back to default/12 if null. */
        @DecimalMin("0.0") BigDecimal monthlyAccrualDays,
        /**
         * Optional seniority bracket schedule (PRD 8.5.2 — milestone 47).
         * When non-null and non-empty, overrides monthlyAccrualDays and
         * defaultAnnualEntitlementDays/12 as the per-employee monthly bump.
         */
        List<SeniorityBracket> seniorityBrackets,
        /** Months after Jan 1 of new year before carried-forward days expire. Null = no mid-year expiry. */
        @Min(1) Integer carryForwardExpiryMonths,
        /** Allows submitting requests beyond the current balance. */
        Boolean negativeBalanceAllowed,
        /** Max overdraft days when negativeBalanceAllowed is true. Null = unlimited. */
        @DecimalMin("0.0") BigDecimal maxNegativeDays) {
}
