package az.millers.hcm.attendance.events;

import java.util.UUID;

public record CorrectionRejectedEvent(
        UUID correctionId,
        UUID employeeId,
        UUID tenantId,
        String rejectedBy) {
}
