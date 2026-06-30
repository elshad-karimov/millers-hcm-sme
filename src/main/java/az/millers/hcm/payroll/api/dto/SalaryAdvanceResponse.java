package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.SalaryAdvance;

public record SalaryAdvanceResponse(
        UUID id,
        UUID employeeId,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        Integer repaymentYear,
        Integer repaymentMonth,
        String status,
        String reason,
        String requestedBy,
        String approvedBy,
        OffsetDateTime approvedAt,
        OffsetDateTime createdAt
) {
    public static SalaryAdvanceResponse from(SalaryAdvance advance) {
        return new SalaryAdvanceResponse(
                advance.getId(),
                advance.getEmployeeId(),
                advance.getRequestedAmount(),
                advance.getApprovedAmount(),
                advance.getRepaymentYear(),
                advance.getRepaymentMonth(),
                advance.getStatus().name(),
                advance.getReason(),
                advance.getRequestedBy(),
                advance.getApprovedBy(),
                advance.getApprovedAt(),
                advance.getCreatedAt()
        );
    }
}
