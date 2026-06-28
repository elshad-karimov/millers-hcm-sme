package az.millers.hcm.leave.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeavePeriodLock;

public record LeavePeriodLockResponse(
        UUID id,
        UUID leaveTypeId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String reason,
        String lockedBy,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static LeavePeriodLockResponse from(LeavePeriodLock l) {
        return new LeavePeriodLockResponse(
                l.getId(),
                l.getLeaveTypeId(),
                l.getPeriodStart(),
                l.getPeriodEnd(),
                l.getReason(),
                l.getLockedBy(),
                l.isActive(),
                l.getCreatedAt(),
                l.getUpdatedAt());
    }
}
