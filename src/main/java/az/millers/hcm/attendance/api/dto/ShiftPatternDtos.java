package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.attendance.domain.PatternAssignment;
import az.millers.hcm.attendance.domain.ShiftPattern;
import az.millers.hcm.attendance.domain.ShiftPatternDay;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** DTOs for the shift-pattern + auto-roster surface (M111). */
public final class ShiftPatternDtos {

    private ShiftPatternDtos() {}

    public record PatternDayRequest(
            @PositiveOrZero int dayIndex,
            /** Null = OFF (rest day). */
            UUID shiftId,
            String notes) {
    }

    public record PatternRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @Min(1) @Max(365) Integer cycleDays,
            Boolean active,
            /** Must list every dayIndex from 0..cycleDays-1, exactly once. */
            @NotNull @Valid List<PatternDayRequest> days) {
    }

    public record PatternDayResponse(
            UUID id,
            int dayIndex,
            UUID shiftId,
            String shiftCode,
            String shiftName,
            String shiftColor,
            String notes) {

        public static PatternDayResponse from(ShiftPatternDay d,
                                                az.millers.hcm.attendance.domain.Shift s) {
            return new PatternDayResponse(
                    d.getId(), d.getDayIndex(), d.getShiftId(),
                    s == null ? null : s.getCode(),
                    s == null ? null : s.getName(),
                    s == null ? null : s.getColor(),
                    d.getNotes());
        }
    }

    public record PatternResponse(
            UUID id,
            String code,
            String name,
            String description,
            int cycleDays,
            boolean active,
            int assignmentCount,
            List<PatternDayResponse> days,
            OffsetDateTime createdAt) {

        public static PatternResponse from(ShiftPattern p,
                                            List<PatternDayResponse> days,
                                            long assignmentCount) {
            return new PatternResponse(
                    p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getCycleDays(), p.isActive(),
                    (int) assignmentCount, days, p.getCreatedAt());
        }
    }

    public record AssignmentRequest(
            @NotNull UUID employeeId,
            @NotNull UUID patternId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @PositiveOrZero Integer anchorDayIndex,
            String notes) {
    }

    public record EndAssignmentRequest(
            @NotNull LocalDate endDate,
            String reason) {
    }

    public record AssignmentResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID patternId,
            String patternCode,
            String patternName,
            LocalDate startDate,
            LocalDate endDate,
            int anchorDayIndex,
            String notes,
            OffsetDateTime updatedAt) {

        public static AssignmentResponse from(PatternAssignment a,
                                                ShiftPattern p,
                                                String employeeName) {
            return new AssignmentResponse(
                    a.getId(), a.getEmployeeId(), employeeName,
                    a.getPatternId(),
                    p == null ? null : p.getCode(),
                    p == null ? null : p.getName(),
                    a.getStartDate(), a.getEndDate(), a.getAnchorDayIndex(),
                    a.getNotes(), a.getUpdatedAt());
        }
    }

    /** Request body for {@code POST /generate-roster}. */
    public record GenerateRosterRequest(
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            /** Null → every employee with an overlapping assignment. */
            List<UUID> employeeIds,
            /**
             * When true, existing RosterEntry rows in the range that aren't
             * locked are overwritten. When false, those rows are preserved
             * (the most common case — generate fills only the gaps).
             */
            Boolean overwriteExisting) {
    }

    /** Summary returned by the generator. */
    public record GenerateRosterResponse(
            int employeesProcessed,
            int rosterRowsCreated,
            int rosterRowsUpdated,
            int rosterRowsSkippedLocked,
            int restDaysSkipped) {
    }
}
