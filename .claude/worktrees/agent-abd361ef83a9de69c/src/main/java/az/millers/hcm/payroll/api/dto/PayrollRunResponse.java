package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;

public record PayrollRunResponse(
        UUID id,
        String runNo,
        int periodYear,
        int periodMonth,
        String jurisdiction,
        String currency,
        PayrollRunStatus status,
        UUID workflowInstanceId,
        BigDecimal totalGross,
        BigDecimal totalIncomeTax,
        BigDecimal totalDsmfEmployee,
        BigDecimal totalDsmfEmployer,
        BigDecimal totalMmiEmployee,
        BigDecimal totalMmiEmployer,
        BigDecimal totalUnemplEmployee,
        BigDecimal totalUnemplEmployer,
        BigDecimal totalNet,
        BigDecimal totalAllowance,
        int employeeCount,
        OffsetDateTime calculatedAt,
        String calculatedBy,
        OffsetDateTime approvedAt,
        String approvedBy,
        OffsetDateTime paidAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        String createdBy) {

    public static PayrollRunResponse from(PayrollRun r) {
        return new PayrollRunResponse(
                r.getId(), r.getRunNo(),
                r.getPeriodYear(), r.getPeriodMonth(),
                r.getJurisdiction(), r.getCurrency(),
                r.getStatus(), r.getWorkflowInstanceId(),
                r.getTotalGross(), r.getTotalIncomeTax(),
                r.getTotalDsmfEmployee(), r.getTotalDsmfEmployer(),
                r.getTotalMmiEmployee(), r.getTotalMmiEmployer(),
                r.getTotalUnemplEmployee(), r.getTotalUnemplEmployer(),
                r.getTotalNet(), r.getTotalAllowance(),
                r.getEmployeeCount(),
                r.getCalculatedAt(), r.getCalculatedBy(),
                r.getApprovedAt(), r.getApprovedBy(),
                r.getPaidAt(), r.getClosedAt(),
                r.getCreatedAt(), r.getCreatedBy());
    }
}
