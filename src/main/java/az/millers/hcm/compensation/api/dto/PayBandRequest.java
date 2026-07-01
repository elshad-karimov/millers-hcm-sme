package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M359 — Pay band create/update request.
 */
public record PayBandRequest(
    String code,
    String name,
    UUID gradeId,
    String currency,
    BigDecimal minSalary,
    BigDecimal midSalary,
    BigDecimal maxSalary,
    BigDecimal q1Salary,
    BigDecimal q3Salary,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Boolean isActive
) {
}
