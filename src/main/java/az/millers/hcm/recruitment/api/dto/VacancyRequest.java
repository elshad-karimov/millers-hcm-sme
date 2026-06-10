package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.recruitment.domain.HiringReason;
import az.millers.hcm.recruitment.domain.RequisitionType;

public record VacancyRequest(
        @NotBlank @Size(max = 200) String title,
        UUID positionId,
        @Size(max = 160) String department,
        @Size(max = 160) String location,
        @NotNull @Min(1) Integer openings,
        String description,
        String requirements,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        @Size(min = 3, max = 3) String currency,
        UUID hiringManagerId,
        UUID recruiterId,
        LocalDate openingDate,
        LocalDate closingDate,
        // ── M274 — requisition fields (all optional; type defaults to
        // NEW_HEADCOUNT server-side so old clients keep working) ──
        RequisitionType requisitionType,
        HiringReason hiringReason,
        LocalDate targetStartDate,
        @Size(max = 64) String costCentre,
        @Size(max = 32) String employmentType,
        UUID replacedEmployeeId) {
}
