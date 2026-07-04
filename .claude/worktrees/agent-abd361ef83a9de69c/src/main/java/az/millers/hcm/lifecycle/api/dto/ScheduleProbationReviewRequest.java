package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ProbationReviewType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Optional manual review scheduling — the auto-create path on contract
 * activation handles the common case, but HR may want to schedule an
 * ad-hoc additional review (e.g. after an extension) via this endpoint.
 */
public record ScheduleProbationReviewRequest(
        @NotNull UUID contractId,
        @NotNull ProbationReviewType reviewType,
        @NotNull LocalDate scheduledDate,
        UUID managerEmployeeId,
        UUID reviewerEmployeeId,
        @Size(max = 4000) String notes) {
}
