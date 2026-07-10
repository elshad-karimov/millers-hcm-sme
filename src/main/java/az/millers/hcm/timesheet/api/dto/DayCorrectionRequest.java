package az.millers.hcm.timesheet.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DayCorrectionRequest(
        @Pattern(regexp = "W|L|S|BT|P|O|H|A") String primaryCode,
        @DecimalMin("0.0") BigDecimal workedHours,
        @DecimalMin("0.0") BigDecimal overtimeHours,
        UUID projectId,
        String taskCode,
        Boolean billable,
        @NotBlank String reason) {
}
