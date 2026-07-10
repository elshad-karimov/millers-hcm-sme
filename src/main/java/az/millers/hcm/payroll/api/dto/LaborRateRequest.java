package az.millers.hcm.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M485: Labor rate request.
 */
public record LaborRateRequest(
    UUID gradeId,
    UUID positionId,
    @NotNull @DecimalMin("0.0") BigDecimal hourlyRate,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
