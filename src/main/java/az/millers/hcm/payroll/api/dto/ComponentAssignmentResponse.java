package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.SalaryComponentAssignment;
import az.millers.hcm.payroll.security.PayrollAccessRoles;

public record ComponentAssignmentResponse(
        UUID id,
        UUID employeeId,
        UUID componentId,
        String componentCode,
        String componentName,
        BigDecimal amountOverride,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String reason,
        OffsetDateTime createdAt) {

    public static ComponentAssignmentResponse from(SalaryComponentAssignment a) {
        boolean canSeeSalary = PayrollAccessRoles.canSeeSalaryAmounts();
        return new ComponentAssignmentResponse(
                a.getId(),
                a.getEmployeeId(),
                a.getComponent().getId(),
                a.getComponent().getCode(),
                a.getComponent().getName(),
                canSeeSalary ? a.getAmountOverride() : null,
                a.getEffectiveFrom(),
                a.getEffectiveTo(),
                a.getReason(),
                a.getCreatedAt()
        );
    }
}
