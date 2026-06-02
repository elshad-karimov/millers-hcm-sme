package az.millers.hcm.leave.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LeaveGroupRequest(
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "code must be UPPER_SNAKE_CASE")
        String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        Boolean defaultGroup,
        Boolean active) {
}
