package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.TerminationReason;
import jakarta.validation.constraints.NotNull;

public record TerminationSubmitRequest(
        @NotNull UUID employeeId,
        @NotNull TerminationReason reasonCode,
        String reasonDetail,
        @NotNull LocalDate noticeDate,
        @NotNull LocalDate lastWorkingDate,
        @NotNull LocalDate effectiveDate,
        String attachmentUrls,
        String note) {
}
