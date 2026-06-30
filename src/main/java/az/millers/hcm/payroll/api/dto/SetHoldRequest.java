package az.millers.hcm.payroll.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SetHoldRequest(
        @NotEmpty List<HoldEntry> holds
) {
    public record HoldEntry(
            @NotNull UUID employeeId,
            String reason
    ) {}
}
