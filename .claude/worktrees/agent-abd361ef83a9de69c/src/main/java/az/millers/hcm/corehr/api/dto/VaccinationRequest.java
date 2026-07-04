package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M137 — body of {@code POST/PUT /api/employees/{id}/vaccinations}.
 *
 * <p>Required fields mirror the {@link az.millers.hcm.corehr.domain.EmployeeVaccination}
 * NOT NULL columns. {@code nextDoseDate} must be on/after
 * {@code administeredDate} (enforced by the DB CHECK) — service-layer
 * validation rejects half-pairs up-front for a clean error.
 */
public record VaccinationRequest(
        @NotBlank @Size(max = 60) String vaccineCode,
        @NotBlank @Size(max = 200) String vaccineName,
        @NotNull LocalDate administeredDate,
        @Size(max = 160) String administeredBy,
        @Size(max = 60) String lotNumber,
        LocalDate nextDoseDate,
        @Size(max = 500) String attachmentUrl,
        @Size(max = 4000) String notes) {
}
