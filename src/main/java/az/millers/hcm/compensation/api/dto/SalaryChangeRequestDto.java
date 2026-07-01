package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compensation.domain.SalaryChangeRequest;
import az.millers.hcm.compensation.domain.SalaryChangeStatus;

public record SalaryChangeRequestDto(
        UUID id,
        UUID employeeId,
        BigDecimal currentSalary,
        BigDecimal proposedSalary,
        String currency,
        UUID changeReasonId,
        LocalDate effectiveDate,
        BigDecimal increasePct,
        Boolean outsideBand,
        Boolean aboveThreshold,
        SalaryChangeStatus status,
        UUID workflowInstanceId,
        String requestedBy,
        OffsetDateTime createdAt,
        OffsetDateTime decidedAt,
        OffsetDateTime appliedAt,
        String note
) {
    public static SalaryChangeRequestDto from(SalaryChangeRequest r) {
        return new SalaryChangeRequestDto(
                r.getId(),
                r.getEmployeeId(),
                r.getCurrentSalary(),
                r.getProposedSalary(),
                r.getCurrency(),
                r.getChangeReasonId(),
                r.getEffectiveDate(),
                r.getIncreasePct(),
                r.getOutsideBand(),
                r.getAboveThreshold(),
                r.getStatus(),
                r.getWorkflowInstanceId(),
                r.getRequestedBy(),
                r.getCreatedAt(),
                r.getDecidedAt(),
                r.getAppliedAt(),
                r.getNote()
        );
    }
}
