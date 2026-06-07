package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeVaccination;

public record VaccinationResponse(
        UUID id,
        UUID employeeId,
        String vaccineCode,
        String vaccineName,
        LocalDate administeredDate,
        String administeredBy,
        String lotNumber,
        LocalDate nextDoseDate,
        String attachmentUrl,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static VaccinationResponse from(EmployeeVaccination v) {
        return new VaccinationResponse(
                v.getId(),
                v.getEmployeeId(),
                v.getVaccineCode(),
                v.getVaccineName(),
                v.getAdministeredDate(),
                v.getAdministeredBy(),
                v.getLotNumber(),
                v.getNextDoseDate(),
                v.getAttachmentUrl(),
                v.getNotes(),
                v.getCreatedAt(),
                v.getCreatedBy(),
                v.getUpdatedAt(),
                v.getUpdatedBy());
    }
}
