package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.CalculationMethod;
import az.millers.hcm.payroll.domain.ComponentKind;
import az.millers.hcm.payroll.domain.SalaryComponent;
import az.millers.hcm.payroll.security.PayrollAccessRoles;

public record SalaryComponentResponse(
        UUID id,
        String code,
        String name,
        ComponentKind kind,
        CalculationMethod calculationMethod,
        BigDecimal defaultAmount,
        BigDecimal percentage,
        Boolean isTaxable,
        Boolean contributionExempt,
        Boolean isStatutory,
        Boolean isActive,
        OffsetDateTime createdAt) {

    public static SalaryComponentResponse from(SalaryComponent c) {
        boolean canSeeSalary = PayrollAccessRoles.canSeeSalaryAmounts();
        return new SalaryComponentResponse(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getKind(),
                c.getCalculationMethod(),
                canSeeSalary ? c.getDefaultAmount() : null,
                canSeeSalary ? c.getPercentage() : null,
                c.getIsTaxable(),
                c.getContributionExempt(),
                c.getIsStatutory(),
                c.getIsActive(),
                c.getCreatedAt()
        );
    }
}
