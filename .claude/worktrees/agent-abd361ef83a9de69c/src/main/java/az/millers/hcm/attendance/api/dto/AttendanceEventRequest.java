package az.millers.hcm.attendance.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.EventType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AttendanceEventRequest(
        UUID employeeId,
        String employeeNo,
        String deviceEmployeeCode,
        @NotNull OffsetDateTime eventTime,
        @NotNull EventType eventType,
        String deviceId,
        String location) {

    @AssertTrue(message = "Either employeeId or employeeNo must be provided")
    public boolean isEmployeeReferenced() {
        return employeeId != null || (employeeNo != null && !employeeNo.isBlank());
    }
}
