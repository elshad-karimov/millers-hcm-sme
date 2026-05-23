package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BonusRun;
import az.millers.hcm.compbenefits.domain.BonusRunStatus;

public record BonusRunResponse(
        UUID id,
        String runNo,
        String name,
        UUID cycleId,
        UUID targetPayrollRunId,
        int periodYear,
        int periodMonth,
        String currency,
        BonusRunStatus status,
        BigDecimal totalAmount,
        int employeeCount,
        OffsetDateTime generatedAt,
        String generatedBy,
        OffsetDateTime pushedAt,
        String pushedBy,
        String note,
        OffsetDateTime createdAt,
        String createdBy) {

    public static BonusRunResponse from(BonusRun r) {
        return new BonusRunResponse(
                r.getId(), r.getRunNo(), r.getName(), r.getCycleId(), r.getTargetPayrollRunId(),
                r.getPeriodYear(), r.getPeriodMonth(), r.getCurrency(), r.getStatus(),
                r.getTotalAmount(), r.getEmployeeCount(),
                r.getGeneratedAt(), r.getGeneratedBy(),
                r.getPushedAt(), r.getPushedBy(),
                r.getNote(), r.getCreatedAt(), r.getCreatedBy());
    }
}
