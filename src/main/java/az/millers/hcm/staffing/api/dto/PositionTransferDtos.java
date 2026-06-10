package az.millers.hcm.staffing.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.staffing.domain.PositionTransfer;
import az.millers.hcm.staffing.domain.TransferStatus;

/** M260 — Position transfer workflow DTOs (PRD §40). */
public final class PositionTransferDtos {

    private PositionTransferDtos() {}

    public record InitiateRequest(
            @NotNull UUID positionId,
            UUID toOrgUnitId,
            @Size(max = 64)  String toCostCentre,
            @Size(max = 160) String toLocation,
            @Size(max = 64)  String transferReason,
            @Size(max = 2000) String notes,
            @NotNull LocalDate effectiveDate) {}

    public record ActionRequest(@Size(max = 2000) String reason) {}

    public record TransferResponse(
            UUID id,
            UUID positionId,
            UUID fromOrgUnitId,
            String fromOrgUnitLabel,
            String fromCostCentre,
            String fromLocation,
            UUID toOrgUnitId,
            String toOrgUnitLabel,
            String toCostCentre,
            String toLocation,
            String transferReason,
            String notes,
            LocalDate effectiveDate,
            TransferStatus status,
            String requestedBy,
            OffsetDateTime requestedAt,
            String submittedBy,
            OffsetDateTime submittedAt,
            String approvedBy,
            OffsetDateTime approvedAt,
            String rejectedBy,
            OffsetDateTime rejectedAt,
            String rejectReason,
            String completedBy,
            OffsetDateTime completedAt,
            String cancelledBy,
            OffsetDateTime cancelledAt,
            String cancelReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static TransferResponse from(PositionTransfer t) {
            return new TransferResponse(
                    t.getId(), t.getPositionId(),
                    t.getFromOrgUnitId(), t.getFromOrgUnitLabel(),
                    t.getFromCostCentre(), t.getFromLocation(),
                    t.getToOrgUnitId(), t.getToOrgUnitLabel(),
                    t.getToCostCentre(), t.getToLocation(),
                    t.getTransferReason(), t.getNotes(),
                    t.getEffectiveDate(), t.getStatus(),
                    t.getRequestedBy(), t.getRequestedAt(),
                    t.getSubmittedBy(), t.getSubmittedAt(),
                    t.getApprovedBy(), t.getApprovedAt(),
                    t.getRejectedBy(), t.getRejectedAt(), t.getRejectReason(),
                    t.getCompletedBy(), t.getCompletedAt(),
                    t.getCancelledBy(), t.getCancelledAt(), t.getCancelReason(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
