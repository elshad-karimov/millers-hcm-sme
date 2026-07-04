package az.millers.hcm.attendance.events;

import java.util.UUID;

public record CorrectionApprovedEvent(
        UUID correctionId,
        UUID employeeId,
        UUID tenantId,
        String approvedBy) {
}
