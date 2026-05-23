package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.PayrollAllowance;

/**
 * One allowance line attached to a payroll run for a given employee
 * (M41). Snapshotted at calc time — see {@link PayrollAllowance}.
 */
public record PayrollAllowanceResponse(
        UUID id,
        UUID runId,
        UUID employeeId,
        UUID employeeAllowanceId,
        UUID allowanceTypeId,
        String allowanceTypeCode,
        String allowanceTypeName,
        BigDecimal amount,
        String currency,
        boolean taxable,
        String note,
        OffsetDateTime createdAt) {

    public static PayrollAllowanceResponse from(PayrollAllowance a) {
        return new PayrollAllowanceResponse(
                a.getId(), a.getRunId(), a.getEmployeeId(),
                a.getEmployeeAllowanceId(),
                a.getAllowanceTypeId(), a.getAllowanceTypeCode(), a.getAllowanceTypeName(),
                a.getAmount(), a.getCurrency(), a.isTaxable(),
                a.getNote(), a.getCreatedAt());
    }
}
