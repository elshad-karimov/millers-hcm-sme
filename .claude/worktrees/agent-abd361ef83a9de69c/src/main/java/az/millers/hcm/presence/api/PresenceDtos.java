package az.millers.hcm.presence.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.presence.domain.PresenceState;

/**
 * M125 — wire DTOs for the presence snapshot endpoint.
 */
public final class PresenceDtos {

    private PresenceDtos() {}

    public record PresenceRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            String department,
            UUID managerId,
            PresenceState state,
            OffsetDateTime since,
            /** Free text — e.g. trip destination or leave reason. */
            String note) {}

    public record PresenceSnapshot(
            LocalDate generatedFor,
            OffsetDateTime generatedAt,
            int total,
            Map<PresenceState, Long> counts,
            List<PresenceRow> rows) {}
}
