package az.millers.hcm.timesheet.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * M484: Timesheet project request.
 */
public record TimesheetProjectRequest(
    @NotBlank String code,
    @NotBlank String name,
    String description,
    BigDecimal billingRate,
    Boolean active
) {}
