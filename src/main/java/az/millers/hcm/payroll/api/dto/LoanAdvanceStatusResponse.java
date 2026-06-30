package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.payroll.domain.SalaryAdvanceStatus;

public record LoanAdvanceStatusResponse(
        List<LoanDetail> activeLoans,
        List<AdvanceDetail> pendingAdvances
) {
    public record LoanDetail(
            UUID loanId,
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal principal,
            BigDecimal monthlyInstallment,
            BigDecimal outstanding,
            String expectedPayoffMonth
    ) {}

    public record AdvanceDetail(
            UUID advanceId,
            UUID employeeId,
            String employeeNo,
            String fullName,
            BigDecimal amount,
            SalaryAdvanceStatus status
    ) {}
}
