package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.PayrollResult;

public record PayrollResultResponse(
        UUID id,
        UUID runId,
        UUID employeeId,
        String payslipNo,
        UUID timesheetId,
        BigDecimal baseSalary,
        BigDecimal workedHours,
        BigDecimal expectedMonthlyHours,
        BigDecimal proRationFactor,
        BigDecimal overtimeHours,
        BigDecimal overtimePay,
        BigDecimal bonusAmount,
        BigDecimal allowanceAmount,
        BigDecimal deductionAmount,
        BigDecimal grossAmount,
        BigDecimal incomeTax,
        BigDecimal dsmfEmployee,
        BigDecimal dsmfEmployer,
        BigDecimal mmiEmployee,
        BigDecimal mmiEmployer,
        BigDecimal unemplEmployee,
        BigDecimal unemplEmployer,
        BigDecimal netAmount,
        String calculationDetails,
        OffsetDateTime createdAt) {

    public static PayrollResultResponse from(PayrollResult r) {
        return new PayrollResultResponse(
                r.getId(), r.getRunId(), r.getEmployeeId(), r.getPayslipNo(),
                r.getTimesheetId(),
                r.getBaseSalary(), r.getWorkedHours(),
                r.getExpectedMonthlyHours(), r.getProRationFactor(),
                r.getOvertimeHours(), r.getOvertimePay(),
                r.getBonusAmount(), r.getAllowanceAmount(), r.getDeductionAmount(),
                r.getGrossAmount(),
                r.getIncomeTax(),
                r.getDsmfEmployee(), r.getDsmfEmployer(),
                r.getMmiEmployee(), r.getMmiEmployer(),
                r.getUnemplEmployee(), r.getUnemplEmployer(),
                r.getNetAmount(), r.getCalculationDetails(),
                r.getCreatedAt());
    }
}
