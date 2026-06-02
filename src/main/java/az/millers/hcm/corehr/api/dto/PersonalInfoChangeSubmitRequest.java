package az.millers.hcm.corehr.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Submit a single personal-info change (M79 / P2-25/26). Submitted by the
 * employee themselves via /api/self/personal-info; the service forces
 * {@code employeeId} to the caller's identity.
 *
 * <p>The field-key whitelist is enforced both at the service layer and via
 * the V64 CHECK constraint — defence in depth.
 */
public record PersonalInfoChangeSubmitRequest(
        UUID employeeId,
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$",
                message = "fieldKey must be camelCase alphanumeric")
        String fieldKey,
        @Size(max = 1000) String newValue,
        @Size(max = 1000) String reason) {
}
