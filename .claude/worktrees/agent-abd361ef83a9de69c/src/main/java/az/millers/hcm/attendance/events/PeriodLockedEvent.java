package az.millers.hcm.attendance.events;

import java.util.UUID;

public record PeriodLockedEvent(
        UUID tenantId,
        int year,
        int month,
        String lockedBy) {
}
