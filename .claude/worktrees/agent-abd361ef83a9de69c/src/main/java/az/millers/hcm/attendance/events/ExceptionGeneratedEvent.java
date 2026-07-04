package az.millers.hcm.attendance.events;

import java.util.UUID;

import az.millers.hcm.attendance.domain.AttendanceException;

public record ExceptionGeneratedEvent(AttendanceException exception, UUID tenantId) {
}
