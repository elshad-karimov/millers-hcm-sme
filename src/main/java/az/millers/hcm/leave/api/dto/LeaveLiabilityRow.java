package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveLiabilityRow(
        UUID employeeId,
        String employeeNo,
        String employeeName,
        String departmentName,
        UUID leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        int year,
        BigDecimal remainingDays,
        BigDecimal monthlyBaseSalary,
        BigDecimal dailyRate,
        BigDecimal liabilityAmount
) {}
