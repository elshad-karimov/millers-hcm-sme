package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeaveBalance;

public record LeaveBalanceResponse(
        UUID id,
        UUID employeeId,
        UUID leaveTypeId,
        int year,
        BigDecimal entitlementDays,
        BigDecimal carriedForwardDays,
        BigDecimal adjustmentDays,
        BigDecimal usedDays,
        BigDecimal reservedDays,
        BigDecimal remainingDays,
        LocalDate carryForwardExpiresAt,
        OffsetDateTime lastRecalculatedAt) {

    public static LeaveBalanceResponse from(LeaveBalance b) {
        return new LeaveBalanceResponse(
                b.getId(), b.getEmployeeId(), b.getLeaveTypeId(), b.getYear(),
                b.getEntitlementDays(), b.getCarriedForwardDays(),
                b.getAdjustmentDays(), b.getUsedDays(), b.getReservedDays(),
                b.remaining(), b.getCarryForwardExpiresAt(), b.getLastRecalculatedAt());
    }
}
