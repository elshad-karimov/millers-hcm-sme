package az.millers.hcm.attendance.events;

import java.util.UUID;

public record OvertimeRequestSubmittedEvent(
        UUID requestId,
        UUID employeeId,
        UUID tenantId,
        int requestedMinutes,
        String submittedBy) {
}
