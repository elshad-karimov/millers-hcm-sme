package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.AllowanceStatus;
import az.millers.hcm.compbenefits.domain.EmployeeAllowance;

public record EmployeeAllowanceResponse(
        UUID id,
        String allowanceNo,
        UUID employeeId,
        UUID allowanceTypeId,
        BigDecimal amount,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        AllowanceStatus status,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static EmployeeAllowanceResponse from(EmployeeAllowance a) {
        return new EmployeeAllowanceResponse(
                a.getId(), a.getAllowanceNo(), a.getEmployeeId(), a.getAllowanceTypeId(),
                a.getAmount(), a.getCurrency(), a.getEffectiveFrom(), a.getEffectiveTo(),
                a.getStatus(), a.getNote(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getCreatedBy(), a.getUpdatedBy());
    }
}
