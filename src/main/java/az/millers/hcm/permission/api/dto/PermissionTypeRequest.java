package az.millers.hcm.permission.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        String description,
        @DecimalMin("0.0") BigDecimal annualLimitHours,
        Boolean paid,
        Boolean requiresAttachment,
        Boolean active) {
}
