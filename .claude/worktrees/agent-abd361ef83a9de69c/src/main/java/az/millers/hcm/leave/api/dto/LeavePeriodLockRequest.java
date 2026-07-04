package az.millers.hcm.leave.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record LeavePeriodLockRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        /** NULL = applies to all leave types. */
        UUID leaveTypeId,
        String reason,
        Boolean active
) {}
