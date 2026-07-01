package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.compensation.domain.PayBand;

/**
 * M359 — Pay band response DTO.
 */
public record PayBandResponse(
    UUID id,
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
    public static PayBandResponse from(PayBand entity) {
        if (entity == null) return null;
        return new PayBandResponse(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getGradeId(),
            entity.getCurrency(),
            entity.getMinSalary(),
            entity.getMidSalary(),
            entity.getMaxSalary(),
            entity.getQ1Salary(),
            entity.getQ3Salary(),
            entity.getEffectiveFrom(),
            entity.getEffectiveTo(),
            entity.getIsActive()
        );
    }
}
