package az.millers.hcm.permission.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PermissionSubmitRequest(
        @NotNull UUID employeeId,
        @NotNull UUID permissionTypeId,
        @NotNull LocalDate permissionDate,
        LocalTime startTime,
        LocalTime endTime,
        @DecimalMin("0.0") BigDecimal durationHours,
        String reason,
        String attachmentUrl) {

    @AssertTrue(message = "Provide either start+end times or an explicit durationHours")
    public boolean isDurationDeterminable() {
        if (durationHours != null && durationHours.signum() > 0) return true;
        return startTime != null && endTime != null && endTime.isAfter(startTime);
    }
}
