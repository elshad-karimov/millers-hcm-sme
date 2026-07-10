package az.millers.hcm.attendance.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * M482: Shift swap request DTO.
 */
public record SwapRequestDto(
    @NotNull UUID rosterEntryId,
    @NotNull UUID fromEmployeeId,
    @NotNull UUID toEmployeeId,
    String notes
) {}
