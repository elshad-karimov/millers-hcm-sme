package az.millers.hcm.attendance.events;

import java.util.UUID;

public record CorrectionSubmittedEvent(
        UUID correctionId,
        UUID employeeId,
        UUID tenantId,
        String submittedBy) {
}
