package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfferRequest(
        @NotNull @DecimalMin("0.0") BigDecimal proposedSalary,
        @Size(min = 3, max = 3) String currency,
        @NotNull LocalDate proposedStartDate,
        String benefits,
        String notes) {
}
