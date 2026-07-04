package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeStatusOverlay;
import az.millers.hcm.corehr.domain.EmployeeStatusOverlay.OverlaySource;
import az.millers.hcm.corehr.domain.EmploymentStatus;

public record StatusOverlayResponse(
        UUID id, UUID employeeId,
        EmploymentStatus status,
        OverlaySource source, UUID sourceId,
        LocalDate effectiveFrom, LocalDate effectiveTo,
        String notes,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static StatusOverlayResponse from(EmployeeStatusOverlay o) {
        return new StatusOverlayResponse(
                o.getId(), o.getEmployeeId(),
                o.getStatus(), o.getSource(), o.getSourceId(),
                o.getEffectiveFrom(), o.getEffectiveTo(), o.getNotes(),
                o.getCreatedAt(), o.getCreatedBy(),
                o.getUpdatedAt(), o.getUpdatedBy());
    }
}
