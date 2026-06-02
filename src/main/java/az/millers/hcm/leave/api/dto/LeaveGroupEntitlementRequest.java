package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record LeaveGroupEntitlementRequest(
        @NotNull UUID leaveTypeId,
        @DecimalMin("0.0") BigDecimal annualEntitlementDays,
        @DecimalMin("0.0") BigDecimal monthlyAccrualDays,
        List<SeniorityBracket> seniorityBrackets,
        String notes) {
}
