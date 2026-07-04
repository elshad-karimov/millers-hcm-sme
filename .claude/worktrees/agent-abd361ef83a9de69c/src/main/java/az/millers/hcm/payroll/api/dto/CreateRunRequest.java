package az.millers.hcm.payroll.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRunRequest(
        @NotNull @Min(2000) Integer periodYear,
        @NotNull @Min(1) @Max(12) Integer periodMonth,
        @Size(min = 2, max = 8) String jurisdiction,
        @Size(min = 3, max = 3) String currency) {
}
