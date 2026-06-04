package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.attendance.domain.RosterEntry;
import az.millers.hcm.attendance.domain.Shift;
import jakarta.validation.constraints.NotNull;

/** DTOs for the roster (M110). */
public final class RosterDtos {

    private RosterDtos() {}

    public record AssignRequest(
            @NotNull UUID employeeId,
            @NotNull UUID shiftId,
            @NotNull LocalDate rosterDate,
            String notes) {
    }

    public record BulkAssignRequest(
            @NotNull List<AssignRequest> entries) {
    }

    public record SwapRequest(
            @NotNull UUID entryAId,
            @NotNull UUID entryBId,
            String reason) {
    }

    public record RosterEntryResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID shiftId,
            String shiftCode,
            String shiftName,
            String shiftColor,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            boolean shiftCrossesMidnight,
            LocalDate rosterDate,
            String notes,
            boolean locked,
            OffsetDateTime updatedAt) {

        public static RosterEntryResponse from(RosterEntry r, Shift s, String employeeName) {
            return new RosterEntryResponse(
                    r.getId(),
                    r.getEmployeeId(),
                    employeeName,
                    r.getShiftId(),
                    s == null ? null : s.getCode(),
                    s == null ? null : s.getName(),
                    s == null ? null : s.getColor(),
                    s == null ? null : s.getStartTime(),
                    s == null ? null : s.getEndTime(),
                    s != null && s.isCrossesMidnight(),
                    r.getRosterDate(),
                    r.getNotes(),
                    r.isLocked(),
                    r.getUpdatedAt());
        }
    }

    /** Weekly/monthly grid roll-up returned by {@code GET /roster}. */
    public record RosterGrid(
            LocalDate from,
            LocalDate to,
            List<EmployeeRow> employees) {

        public record EmployeeRow(
                UUID employeeId,
                String employeeName,
                List<RosterEntryResponse> entries) {
        }
    }
}
