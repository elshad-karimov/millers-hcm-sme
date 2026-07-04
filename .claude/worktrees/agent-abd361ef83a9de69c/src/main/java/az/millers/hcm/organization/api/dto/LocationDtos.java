package az.millers.hcm.organization.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.organization.domain.Location;
import az.millers.hcm.organization.domain.LocationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** M141 — wire DTOs for the Location master. */
public final class LocationDtos {

    private LocationDtos() {}

    public record LocationRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull LocationType locationType,
            @Pattern(regexp = "^[A-Z]{2}$", message = "country must be ISO 3166-1 alpha-2")
            String country,
            @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 500) String address,
            @DecimalMin(value = "-90.0")  @DecimalMax(value = "90.0")  BigDecimal latitude,
            @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") BigDecimal longitude,
            @Size(max = 60)  String timezone,
            @Size(max = 8)   String holidayJurisdiction,
            @Size(max = 60)  String workCalendarCode,
            UUID defaultShiftGroupId,
            UUID branchManagerId,
            UUID legalEntityId,
            @Size(max = 64)  String costCentreCode,
            @Size(max = 32)  String phone,
            @Email @Size(max = 160) String email,
            Boolean active,
            @Size(max = 4000) String notes) {}

    public record LocationResponse(
            UUID id,
            String code,
            String name,
            LocationType locationType,
            String country,
            String city,
            String region,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String timezone,
            String holidayJurisdiction,
            String workCalendarCode,
            UUID defaultShiftGroupId,
            UUID branchManagerId,
            UUID legalEntityId,
            String costCentreCode,
            String phone,
            String email,
            boolean active,
            String notes,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static LocationResponse from(Location l) {
            return new LocationResponse(
                    l.getId(), l.getCode(), l.getName(), l.getLocationType(),
                    l.getCountry(), l.getCity(), l.getRegion(), l.getAddress(),
                    l.getLatitude(), l.getLongitude(), l.getTimezone(),
                    l.getHolidayJurisdiction(), l.getWorkCalendarCode(),
                    l.getDefaultShiftGroupId(), l.getBranchManagerId(), l.getLegalEntityId(),
                    l.getCostCentreCode(), l.getPhone(), l.getEmail(),
                    l.isActive(), l.getNotes(),
                    l.getCreatedAt(), l.getCreatedBy(), l.getUpdatedAt(), l.getUpdatedBy());
        }
    }
}
