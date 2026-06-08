package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PositionRequest(
        @NotBlank @Size(max = 200) String title,
        /** M146 / §8 — optional parent in the position hierarchy. */
        UUID parentPositionId,
        UUID orgUnitId,
        @Size(max = 200) String orgUnitLabel,
        @Size(max = 32) String grade,
        @Size(max = 64) String jobFamily,
        @Size(max = 32) String jobLevel,
        @NotNull @Min(0) Integer approvedHeadcount,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 32) String employmentType,
        @Size(max = 64) String costCentre,
        @Size(max = 64) String budgetCode,
        @Size(max = 160) String location,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
