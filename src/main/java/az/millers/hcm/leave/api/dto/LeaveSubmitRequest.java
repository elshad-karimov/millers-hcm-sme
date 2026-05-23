package az.millers.hcm.leave.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record LeaveSubmitRequest(
        @NotNull UUID employeeId,
        @NotNull UUID leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        Boolean halfDay,
        String reason,
        UUID replacementEmployeeId,
        String attachmentUrl) {
}
