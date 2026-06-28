package az.millers.hcm.attendance.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.AttendancePeriod;

public class PeriodDtos {

    public record PeriodLockRequest(int year, int month, String notes) {
    }

    public record PeriodResponse(
            UUID id,
            UUID tenantId,
            int year,
            int month,
            String status,
            OffsetDateTime lockedAt,
            String lockedBy,
            OffsetDateTime unlockedAt,
            String unlockedBy,
            Integer employeeCountAtLock,
            String notes,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static PeriodResponse from(AttendancePeriod entity) {
            return new PeriodResponse(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getYear(),
                    entity.getMonth(),
                    entity.getStatus(),
                    entity.getLockedAt(),
                    entity.getLockedBy(),
                    entity.getUnlockedAt(),
                    entity.getUnlockedBy(),
                    entity.getEmployeeCountAtLock(),
                    entity.getNotes(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt());
        }
    }
}
