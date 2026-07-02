package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * M370 — Out-of-band salary report row.
 */
public record OutOfBandReportRow(
    UUID employeeId,
    String employeeNo,
    String employeeName,
    String department,
    String gradeCode,
    String bandCode,
    BigDecimal minSalary,
    BigDecimal maxSalary,
    BigDecimal actualSalary,
    BigDecimal deltaFromMin,
    BigDecimal deltaFromMax,
    String position
) {}
