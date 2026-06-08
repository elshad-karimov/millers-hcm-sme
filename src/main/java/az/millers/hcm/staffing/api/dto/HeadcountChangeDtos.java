package az.millers.hcm.staffing.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.staffing.domain.HeadcountChangeRequest;

/** DTOs for the M156 headcount-change-request workflow. */
public final class HeadcountChangeDtos {

    private HeadcountChangeDtos() {}

    public record HeadcountChangeSubmitRequest(
            @NotNull
            @Min(-500)
            @Max(500)
            Integer requestedDelta,
            @Size(max = 2000)
            String reason) {}

    public record HeadcountChangeResponse(
            UUID id,
            UUID positionId,
            int requestedDelta,
            String reason,
            String status,
            UUID workflowInstanceId,
            String requestedBy,
            OffsetDateTime requestedAt,
            String approvedBy,
            OffsetDateTime approvedAt,
            String rejectedBy,
            OffsetDateTime rejectedAt,
            String rejectReason,
            OffsetDateTime createdAt) {

        public static HeadcountChangeResponse from(HeadcountChangeRequest r) {
            return new HeadcountChangeResponse(
                    r.getId(),
                    r.getPositionId(),
                    r.getRequestedDelta(),
                    r.getReason(),
                    r.getStatus(),
                    r.getWorkflowInstanceId(),
                    r.getRequestedBy(),
                    r.getRequestedAt(),
                    r.getApprovedBy(),
                    r.getApprovedAt(),
                    r.getRejectedBy(),
                    r.getRejectedAt(),
                    r.getRejectReason(),
                    r.getCreatedAt());
        }
    }
}
