package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ChangeType;
import jakarta.validation.constraints.NotNull;

public record ContractChangeSubmitRequest(
        @NotNull UUID employeeId,
        @NotNull ChangeType changeType,
        @NotNull LocalDate effectiveDate,
        String reason,
        @NotNull Map<String, Object> newValue,
        String attachmentUrls,
        String note) {
}
