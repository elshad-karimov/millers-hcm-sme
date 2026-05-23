package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeaveType;

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
        Integer maxConsecutiveDays,
        boolean excludeWeekends,
        boolean excludeHolidays,
        boolean active,
        boolean accruesMonthly,
        BigDecimal monthlyAccrualDays,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static LeaveTypeResponse from(LeaveType t) {
        return new LeaveTypeResponse(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.isPaid(), t.isRequiresAttachment(), t.isRequiresReplacement(),
                t.getDefaultAnnualEntitlementDays(), t.getCarryForwardLimitDays(),
                t.getMaxConsecutiveDays(), t.isExcludeWeekends(), t.isExcludeHolidays(),
                t.isActive(), t.isAccruesMonthly(), t.getMonthlyAccrualDays(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
