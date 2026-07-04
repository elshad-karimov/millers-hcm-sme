package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.OccupancyType;
import az.millers.hcm.staffing.domain.PositionOccupancy;
import az.millers.hcm.staffing.domain.PositionReplacement;
import az.millers.hcm.staffing.domain.ReplacementAction;
import az.millers.hcm.staffing.domain.ReplacementStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PositionOccupancyDtos {

    private PositionOccupancyDtos() {}

    // ── Occupancy ──────────────────────────────────────────────────

    public record OccupancyRequest(
            @NotNull UUID positionId,
            @NotNull UUID employeeId,
            OccupancyType occupancyType,
            BigDecimal fteAllocation,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = 64) String endReason,
            @Size(max = 2000) String endNotes,
            UUID homePositionId,
            BigDecimal actingAllowance,
            @Size(min = 3, max = 3) String actingAllowanceCurrency,
            @Size(max = 2000) String notes) {

        public PositionOccupancy toEntity() {
            PositionOccupancy o = new PositionOccupancy();
            o.setPositionId(positionId);
            o.setEmployeeId(employeeId);
            o.setOccupancyType(occupancyType == null ? OccupancyType.PRIMARY : occupancyType);
            if (fteAllocation != null) o.setFteAllocation(fteAllocation);
            o.setStartDate(startDate);
            o.setEndDate(endDate);
            o.setEndReason(endReason);
            o.setEndNotes(endNotes);
            o.setHomePositionId(homePositionId);
            o.setActingAllowance(actingAllowance);
            o.setActingAllowanceCurrency(actingAllowanceCurrency);
            o.setNotes(notes);
            return o;
        }
    }

    public record EndOccupancyRequest(
            LocalDate endDate,
            @Size(max = 64) String reason,
            @Size(max = 2000) String notes) {}

    public record OccupancyResponse(
            UUID id, UUID positionId, UUID employeeId,
            OccupancyType occupancyType,
            BigDecimal fteAllocation,
            LocalDate startDate, LocalDate endDate,
            String endReason, String endNotes,
            UUID homePositionId,
            BigDecimal actingAllowance,
            String actingAllowanceCurrency,
            String notes,
            OffsetDateTime createdAt, String createdBy,
            OffsetDateTime updatedAt, String updatedBy) {

        public static OccupancyResponse from(PositionOccupancy o) {
            return new OccupancyResponse(o.getId(), o.getPositionId(), o.getEmployeeId(),
                    o.getOccupancyType(), o.getFteAllocation(),
                    o.getStartDate(), o.getEndDate(),
                    o.getEndReason(), o.getEndNotes(),
                    o.getHomePositionId(),
                    o.getActingAllowance(), o.getActingAllowanceCurrency(),
                    o.getNotes(),
                    o.getCreatedAt(), o.getCreatedBy(),
                    o.getUpdatedAt(), o.getUpdatedBy());
        }
    }

    // ── Replacement ────────────────────────────────────────────────

    public record ReplacementRequest(
            @NotNull UUID positionId,
            @NotNull UUID leavingEmployeeId,
            UUID leavingOccupancyId,
            @Size(max = 64) String reason,
            @NotNull LocalDate lastWorkingDay,
            ReplacementAction action,
            UUID replacementEmployeeId,
            LocalDate replacementStartDate,
            Integer handoverOverlapDays,
            UUID vacancyId,
            @Size(max = 2000) String notes) {

        public PositionReplacement toEntity() {
            PositionReplacement r = new PositionReplacement();
            r.setPositionId(positionId);
            r.setLeavingEmployeeId(leavingEmployeeId);
            r.setLeavingOccupancyId(leavingOccupancyId);
            r.setReason(reason);
            r.setLastWorkingDay(lastWorkingDay);
            if (action != null) r.setAction(action);
            r.setReplacementEmployeeId(replacementEmployeeId);
            r.setReplacementStartDate(replacementStartDate);
            if (handoverOverlapDays != null) r.setHandoverOverlapDays(handoverOverlapDays);
            r.setVacancyId(vacancyId);
            r.setNotes(notes);
            return r;
        }
    }

    public record ReplacementResponse(
            UUID id, UUID positionId, UUID leavingEmployeeId,
            UUID leavingOccupancyId,
            String reason, LocalDate lastWorkingDay,
            ReplacementAction action,
            UUID replacementEmployeeId, LocalDate replacementStartDate,
            Integer handoverOverlapDays, UUID vacancyId,
            ReplacementStatus status,
            String submittedBy, OffsetDateTime submittedAt,
            String approvedBy, OffsetDateTime approvedAt,
            String rejectedBy, OffsetDateTime rejectedAt, String rejectReason,
            OffsetDateTime completedAt,
            OffsetDateTime cancelledAt, String cancelReason,
            String notes,
            OffsetDateTime createdAt, String createdBy,
            OffsetDateTime updatedAt, String updatedBy) {

        public static ReplacementResponse from(PositionReplacement r) {
            return new ReplacementResponse(r.getId(), r.getPositionId(), r.getLeavingEmployeeId(),
                    r.getLeavingOccupancyId(),
                    r.getReason(), r.getLastWorkingDay(),
                    r.getAction(),
                    r.getReplacementEmployeeId(), r.getReplacementStartDate(),
                    r.getHandoverOverlapDays(), r.getVacancyId(),
                    r.getStatus(),
                    r.getSubmittedBy(), r.getSubmittedAt(),
                    r.getApprovedBy(), r.getApprovedAt(),
                    r.getRejectedBy(), r.getRejectedAt(), r.getRejectReason(),
                    r.getCompletedAt(),
                    r.getCancelledAt(), r.getCancelReason(),
                    r.getNotes(),
                    r.getCreatedAt(), r.getCreatedBy(),
                    r.getUpdatedAt(), r.getUpdatedBy());
        }
    }

    public record ReasonRequest(@Size(max = 2000) String reason) {}
}
