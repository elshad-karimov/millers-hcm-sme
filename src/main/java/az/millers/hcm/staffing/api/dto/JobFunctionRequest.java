package az.millers.hcm.staffing.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JobFunctionRequest(
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "code must be uppercase alphanumeric")
        String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        UUID jobFamilyId,
        Boolean active) {
}
