package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.payroll.domain.ComponentKind;

public record PeriodSummaryResponse(
        UUID runId,
        int periodYear,
        int periodMonth,
        String runNo,
        BigDecimal totalGross,
        BigDecimal totalNet,
        BigDecimal totalTax,
        BigDecimal totalDsmfEmployee,
        BigDecimal totalMmiEmployee,
        BigDecimal totalUnemplEmployee,
        List<EmployeeDetail> employees
) {
    public record EmployeeDetail(
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal gross,
            BigDecimal net,
            BigDecimal tax,
            BigDecimal dsmfEmployee,
            BigDecimal mmiEmployee,
            BigDecimal unemplEmployee,
            List<ComponentLine> components
    ) {}

    public record ComponentLine(
            String code,
            String name,
            ComponentKind kind,
            BigDecimal amount
    ) {}
}
