package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeHealth;

public record HealthResponse(
        UUID id,
        UUID employeeId,
        LocalDate fitnessCertificateDate,
        LocalDate nextExamDate,
        String occupationalHealthNotes,
        String restrictions,
        boolean confidential,
        // M137 — Section 18 disability
        String disabilityStatus,
        Integer disabilityPercent,
        String disabilityNote,
        String accommodationsNote,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static HealthResponse from(EmployeeHealth e) {
        return new HealthResponse(
                e.getId(),
                e.getEmployeeId(),
                e.getFitnessCertificateDate(),
                e.getNextExamDate(),
                e.getOccupationalHealthNotes(),
                e.getRestrictions(),
                e.isConfidential(),
                e.getDisabilityStatus(),
                e.getDisabilityPercent(),
                e.getDisabilityNote(),
                e.getAccommodationsNote(),
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getUpdatedAt(),
                e.getUpdatedBy());
    }
}
