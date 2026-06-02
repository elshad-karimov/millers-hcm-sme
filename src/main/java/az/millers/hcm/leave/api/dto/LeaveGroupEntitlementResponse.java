package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeaveGroupEntitlement;

public record LeaveGroupEntitlementResponse(
        UUID id,
        UUID leaveGroupId,
        UUID leaveTypeId,
        BigDecimal annualEntitlementDays,
        BigDecimal monthlyAccrualDays,
        List<SeniorityBracket> seniorityBrackets,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static LeaveGroupEntitlementResponse from(LeaveGroupEntitlement e) {
        return new LeaveGroupEntitlementResponse(
                e.getId(),
                e.getLeaveGroupId(),
                e.getLeaveTypeId(),
                e.getAnnualEntitlementDays(),
                e.getMonthlyAccrualDays(),
                e.getSeniorityBrackets(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getUpdatedAt(),
                e.getUpdatedBy());
    }
}
