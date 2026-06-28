package az.millers.hcm.leave.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.leave.domain.AbsenceConversionStatus;
import az.millers.hcm.leave.domain.UnauthorizedAbsenceConversion;

public record AbsenceScanResult(
        UUID id,
        UUID employeeId,
        String employeeName,
        String employeeNo,
        LocalDate absenceDate,
        AbsenceConversionStatus status,
        UUID leaveTypeId,
        String leaveTypeName,
        UUID leaveRequestId,
        String notes,
        String resolvedBy,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt
) {
    public static AbsenceScanResult of(UnauthorizedAbsenceConversion u,
                                       String employeeName,
                                       String employeeNo,
                                       String leaveTypeName) {
        return new AbsenceScanResult(
                u.getId(),
                u.getEmployeeId(),
                employeeName,
                employeeNo,
                u.getAbsenceDate(),
                u.getStatus(),
                u.getLeaveTypeId(),
                leaveTypeName,
                u.getLeaveRequestId(),
                u.getNotes(),
                u.getResolvedBy(),
                u.getResolvedAt(),
                u.getCreatedAt());
    }
}
