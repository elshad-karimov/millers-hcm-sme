package az.millers.hcm.permission.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.permission.domain.PermissionBalance;

public record PermissionBalanceResponse(
        UUID id,
        UUID employeeId,
        UUID permissionTypeId,
        int year,
        BigDecimal limitHours,
        BigDecimal adjustmentHours,
        BigDecimal usedHours,
        BigDecimal reservedHours,
        BigDecimal remainingHours,
        OffsetDateTime lastRecalculatedAt) {

    public static PermissionBalanceResponse from(PermissionBalance b) {
        return new PermissionBalanceResponse(
                b.getId(), b.getEmployeeId(), b.getPermissionTypeId(), b.getYear(),
                b.getLimitHours(), b.getAdjustmentHours(),
                b.getUsedHours(), b.getReservedHours(),
                b.remaining(), b.getLastRecalculatedAt());
    }
}
