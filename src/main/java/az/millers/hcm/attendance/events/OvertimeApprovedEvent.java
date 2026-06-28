package az.millers.hcm.attendance.events;

import java.util.UUID;

public record OvertimeApprovedEvent(
        UUID requestId,
        UUID employeeId,
        UUID tenantId,
        String approvedBy) {
}
