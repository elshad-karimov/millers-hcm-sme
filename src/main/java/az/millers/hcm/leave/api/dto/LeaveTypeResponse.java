package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.LeaveUnit;

public record LeaveTypeResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean paid,
        boolean requiresAttachment,
        boolean requiresReplacement,
        BigDecimal defaultAnnualEntitlementDays,
        BigDecimal carryForwardLimitDays,
        Integer carryForwardExpiryMonths,
        Integer maxConsecutiveDays,
        boolean excludeWeekends,
        boolean excludeHolidays,
        boolean active,
        boolean accruesMonthly,
        BigDecimal monthlyAccrualDays,
        /** Seniority bracket schedule, empty list when none configured (M47). */
        List<SeniorityBracket> seniorityBrackets,
        /**
         * M151 — when true, this type's annual entitlement is resolved from
         * itemised components rather than the monthly accrual chain. Only
         * these types have an entitlement breakdown to show.
         */
        boolean entitlementComponentsEnabled,
        boolean negativeBalanceAllowed,
        BigDecimal maxNegativeDays,
        /** M341: How this leave type is measured and requested. */
        LeaveUnit leaveUnit,
        /** M341: Working hours per day for HOURS-unit types. */
        BigDecimal hoursPerDay,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static LeaveTypeResponse from(LeaveType t) {
        List<SeniorityBracket> brackets = t.getSeniorityBrackets() != null
                ? t.getSeniorityBrackets()
                : List.of();
        return new LeaveTypeResponse(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.isPaid(), t.isRequiresAttachment(), t.isRequiresReplacement(),
                t.getDefaultAnnualEntitlementDays(), t.getCarryForwardLimitDays(),
                t.getCarryForwardExpiryMonths(),
                t.getMaxConsecutiveDays(), t.isExcludeWeekends(), t.isExcludeHolidays(),
                t.isActive(), t.isAccruesMonthly(), t.getMonthlyAccrualDays(),
                brackets,
                t.isEntitlementComponentsEnabled(),
                t.isNegativeBalanceAllowed(), t.getMaxNegativeDays(),
                t.getLeaveUnit() != null ? t.getLeaveUnit() : LeaveUnit.DAYS,
                t.getHoursPerDay(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
