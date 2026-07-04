package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;

import az.millers.hcm.lifecycle.domain.ProbationOutcome;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for closing out a probation review — manager + HR feedback, the
 * outcome decision, and the effective date that decision takes hold.
 */
public record CompleteProbationReviewRequest(
        @NotNull ProbationOutcome outcome,
        @NotNull LocalDate completedDate,
        LocalDate effectiveDate,
        @Size(max = 4000) String managerFeedback,
        @Min(1) @Max(5) Integer managerRating,
        @Size(max = 4000) String hrFeedback,
        @Min(1) @Max(5) Integer hrRating,
        @Size(max = 4000) String notes,
        /** Required when outcome == EXTENDED — the new probation end date. */
        LocalDate newProbationEndDate) {
}
