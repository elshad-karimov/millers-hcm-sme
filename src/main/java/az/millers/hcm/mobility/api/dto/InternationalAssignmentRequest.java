package az.millers.hcm.mobility.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InternationalAssignmentRequest(
    @NotNull UUID employeeId,
    @NotBlank String hostCountry,
    String hostCity,
    String hostEntity,
    String purpose,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    String status,
    String visaType,
    LocalDate visaExpiry,
    BigDecimal housingAllowance,
    BigDecimal colaAmount,
    BigDecimal hardshipAmount,
    String notes
) {}
