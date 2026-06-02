package az.millers.hcm.lifecycle.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppealRequest(
        @NotBlank @Size(max = 8000) String reason) {
}
