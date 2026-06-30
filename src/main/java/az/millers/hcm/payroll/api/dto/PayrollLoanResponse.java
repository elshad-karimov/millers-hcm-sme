package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.PayrollLoan;

public record PayrollLoanResponse(
        UUID id,
        UUID employeeId,
        BigDecimal principalAmount,
        BigDecimal monthlyInstallment,
        BigDecimal outstandingBalance,
        BigDecimal amountRepaid,
        int startDeductionYear,
        int startDeductionMonth,
        String status,
        String description,
        String approvedBy,
        OffsetDateTime approvedAt,
        OffsetDateTime createdAt
) {
    public static PayrollLoanResponse from(PayrollLoan loan) {
        return new PayrollLoanResponse(
                loan.getId(),
                loan.getEmployeeId(),
                loan.getPrincipalAmount(),
                loan.getMonthlyInstallment(),
                loan.getOutstandingBalance(),
                loan.getAmountRepaid(),
                loan.getStartDeductionYear(),
                loan.getStartDeductionMonth(),
                loan.getStatus().name(),
                loan.getDescription(),
                loan.getApprovedBy(),
                loan.getApprovedAt(),
                loan.getCreatedAt()
        );
    }
}
