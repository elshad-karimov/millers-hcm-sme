package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.EmployeeCompensation;

public record CompensationResponse(
        UUID id,
        UUID employeeId,
        BigDecimal monthlyBaseSalary,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String reason,
        OffsetDateTime createdAt,
        String createdBy) {

    public static CompensationResponse from(EmployeeCompensation c) {
        return new CompensationResponse(
                c.getId(), c.getEmployeeId(),
                c.getMonthlyBaseSalary(), c.getCurrency(),
                c.getEffectiveFrom(), c.getEffectiveTo(),
                c.getReason(), c.getCreatedAt(), c.getCreatedBy());
    }
}
