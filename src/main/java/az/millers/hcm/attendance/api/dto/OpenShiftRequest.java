package az.millers.hcm.attendance.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * M482: Open shift request.
 */
public record OpenShiftRequest(
    @NotNull UUID shiftId,
    @NotNull LocalDate shiftDate,
    UUID orgUnitId,
    @NotNull @Min(1) Integer slots,
    String notes
) {}
