package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;

import az.millers.hcm.corehr.domain.EmploymentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContractRequest(
        @NotNull EmploymentType contractType,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        LocalDate probationEndDate,
        @Min(0) Integer noticePeriodDays,
        Boolean hasConfidentiality,
        LocalDate nonCompeteEndDate,
        @Size(max = 4000) String notes) {
}
