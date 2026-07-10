package az.millers.hcm.contingent.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContractorEngagementRequest(
    @NotNull UUID employeeId,
    UUID vendorAgencyId,
    @NotNull LocalDate contractStart,
    LocalDate contractEnd,
    BigDecimal rate,
    String rateUnit,
    String poNumber,
    Integer tenureAlertDays,
    String status,
    String notes
) {}
