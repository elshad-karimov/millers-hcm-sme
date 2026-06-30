package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EmployerCostReportResponse(
        UUID runId,
        int periodYear,
        int periodMonth,
        BigDecimal totalGross,
        BigDecimal totalDsmfEmployer,
        BigDecimal totalMmiEmployer,
        BigDecimal totalUnemplEmployer,
        BigDecimal totalCost,
        List<EmployeeCost> employees
) {
    public record EmployeeCost(
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal gross,
            BigDecimal dsmfEmployer,
            BigDecimal mmiEmployer,
            BigDecimal unemplEmployer,
            BigDecimal totalCost
    ) {}
}
