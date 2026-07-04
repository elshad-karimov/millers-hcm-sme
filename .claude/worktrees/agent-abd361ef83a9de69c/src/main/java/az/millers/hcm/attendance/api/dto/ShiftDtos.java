package az.millers.hcm.attendance.api.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.Shift;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** DTOs for the shift catalog (M110). */
public final class ShiftDtos {

    private ShiftDtos() {}

    public record ShiftRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @PositiveOrZero Integer breakMinutes,
            String color,
            Boolean active) {
    }

    public record ShiftResponse(
            UUID id,
            String code,
            String name,
            String description,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            boolean crossesMidnight,
            int durationMinutes,
            String color,
            boolean active,
            OffsetDateTime createdAt) {

        public static ShiftResponse from(Shift s) {
            return new ShiftResponse(
                    s.getId(), s.getCode(), s.getName(), s.getDescription(),
                    s.getStartTime(), s.getEndTime(),
                    s.getBreakMinutes(), s.isCrossesMidnight(), s.durationMinutes(),
                    s.getColor(), s.isActive(), s.getCreatedAt());
        }
    }
}
