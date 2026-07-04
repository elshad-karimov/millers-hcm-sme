package az.millers.hcm.attendance.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.AttendanceEvent;
import az.millers.hcm.attendance.domain.EventType;

public record AttendanceEventResponse(
        UUID id,
        UUID employeeId,
        String deviceEmployeeCode,
        OffsetDateTime eventTime,
        EventType eventType,
        String deviceId,
        String location,
        String source,
        OffsetDateTime importedAt) {

    public static AttendanceEventResponse from(AttendanceEvent e) {
        return new AttendanceEventResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getDeviceEmployeeCode(),
                e.getEventTime(),
                e.getEventType(),
                e.getDeviceId(),
                e.getLocation(),
                e.getSource(),
                e.getImportedAt());
    }
}
