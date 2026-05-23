package az.millers.hcm.staffing.api.dto;

import az.millers.hcm.staffing.domain.VacancyState;
import jakarta.validation.constraints.NotNull;

public record VacancyStateChangeRequest(
        @NotNull VacancyState newState,
        String reason) {
}
