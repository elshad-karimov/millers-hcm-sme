package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.Holiday;

public record HolidayResponse(
        UUID id,
        String jurisdiction,
        LocalDate holidayDate,
        String name,
        String holidayType,
        boolean recurring,
        String notes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static HolidayResponse from(Holiday h) {
        return new HolidayResponse(
                h.getId(), h.getJurisdiction(), h.getHolidayDate(), h.getName(),
                h.getHolidayType(), h.isRecurring(), h.getNotes(), h.isActive(),
                h.getCreatedAt(), h.getUpdatedAt());
    }
}
