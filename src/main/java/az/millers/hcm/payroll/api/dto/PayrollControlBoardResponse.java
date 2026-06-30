package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.domain.RunType;

public record PayrollControlBoardResponse(
        CurrentRunSummary currentRun,
        int headcount,
        BigDecimal totalGross,
        BigDecimal totalNet,
        BigDecimal totalTax,
        BigDecimal momGrossVariancePct,
        BigDecimal outstandingLoanBalance,
        int pendingAdvanceCount
) {
    public record CurrentRunSummary(
            UUID id,
            int periodYear,
            int periodMonth,
            PayrollRunStatus status,
            RunType runType
    ) {}
}
