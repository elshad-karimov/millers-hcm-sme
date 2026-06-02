package az.millers.hcm.letters.api.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Submit a new letter request (M77 / P2-17). {@code employeeId} is forced to
 * the caller in the self-service shortcut endpoint.
 */
public record LetterSubmitRequest(
        @NotNull UUID employeeId,
        @NotNull UUID templateId,
        @Size(max = 500) String purpose,
        Map<String, Object> customFields) {
}
