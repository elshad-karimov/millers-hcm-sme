package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PayrollVarianceResponse(
        UUID currentRunId,
        UUID priorRunId,
        VarianceSummary summary,
        List<EmployeeVariance> employees) {

    public record VarianceSummary(
            BigDecimal totalGrossChange,
            BigDecimal pctChange,
            int highVarianceCount,
            int newEmployeeCount,
            int absentEmployeeCount) {
    }

    public record EmployeeVariance(
            UUID employeeId,
            String employeeNo,
            String name,
            BigDecimal priorGross,
            BigDecimal currentGross,
            BigDecimal grossDelta,
            BigDecimal grossDeltaPct,
            BigDecimal netDelta,
            List<String> flags) {
    }
}
