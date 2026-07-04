package az.millers.hcm.businesstrip.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.businesstrip.domain.TripType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BusinessTripSubmitRequest(
        @NotNull UUID employeeId,
        @NotNull TripType tripType,
        @Size(max = 80) String destinationCountry,
        @NotBlank @Size(max = 120) String destinationCity,
        String purpose,
        @Size(max = 120) String project,
        @Size(max = 64) String costCentre,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(min = 3, max = 3) String currency,
        @DecimalMin("0.0") BigDecimal dailyAllowance,
        @DecimalMin("0.0") BigDecimal requestedAdvance,
        Boolean mealsProvided,
        Boolean accommodationProvided,
        String attachmentUrls) {
}
