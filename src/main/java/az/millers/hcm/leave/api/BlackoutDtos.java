package az.millers.hcm.leave.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.leave.domain.BlackoutScope;
import az.millers.hcm.leave.domain.BlackoutSeverity;

/**
 * M123 — wire DTOs for the blackout admin surface and the leave-form
 * preview endpoint.
 */
public final class BlackoutDtos {

    private BlackoutDtos() {}

    public record BlackoutRequest(
            String name,
            String description,
            BlackoutScope scope,
            UUID orgUnitId,
            UUID leaveTypeId,
            LocalDate startDate,
            LocalDate endDate,
            BlackoutSeverity severity,
            String reason,
            Boolean active) {}

    public record BlackoutResponse(
            UUID id,
            String name,
            String description,
            BlackoutScope scope,
            UUID orgUnitId,
            String orgUnitName,
            UUID leaveTypeId,
            String leaveTypeCode,
            LocalDate startDate,
            LocalDate endDate,
            BlackoutSeverity severity,
            String reason,
            boolean active,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {}

    public record PreviewRequest(
            UUID employeeId,
            UUID leaveTypeId,
            LocalDate startDate,
            LocalDate endDate) {}

    public record PreviewMatch(
            UUID id,
            String name,
            BlackoutScope scope,
            BlackoutSeverity severity,
            LocalDate startDate,
            LocalDate endDate,
            String reason) {}

    public record PreviewResponse(
            BlackoutSeverity worstSeverity,
            String blockMessage,
            List<PreviewMatch> matches) {}
}
