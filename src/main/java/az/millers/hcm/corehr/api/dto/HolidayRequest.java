package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HolidayRequest(
        @Size(max = 8) String jurisdiction,
        @NotNull LocalDate holidayDate,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 32) String holidayType,
        Boolean recurring,
        String notes,
        Boolean active) {
}
