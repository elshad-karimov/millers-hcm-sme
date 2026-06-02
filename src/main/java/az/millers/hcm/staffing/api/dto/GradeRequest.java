package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GradeRequest(
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "code must be uppercase alphanumeric")
        String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        Integer level,
        @DecimalMin("0.0") BigDecimal minSalary,
        @DecimalMin("0.0") BigDecimal maxSalary,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
        String currency,
        Boolean active) {
}
