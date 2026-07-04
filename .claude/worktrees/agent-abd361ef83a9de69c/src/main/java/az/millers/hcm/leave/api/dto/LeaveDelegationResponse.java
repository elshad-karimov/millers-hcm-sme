package az.millers.hcm.leave.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.leave.domain.DelegationStatus;
import az.millers.hcm.leave.domain.LeaveDelegation;

public record LeaveDelegationResponse(
        UUID id,
        UUID leaveRequestId,
        UUID delegatorId,
        String delegatorName,
        UUID delegateId,
        String delegateName,
        String delegationScope,
        DelegationStatus status,
        String delegateNotes,
        OffsetDateTime respondedAt,
        OffsetDateTime createdAt
) {
    public static LeaveDelegationResponse of(LeaveDelegation d,
                                              String delegatorName,
                                              String delegateName) {
        return new LeaveDelegationResponse(
                d.getId(), d.getLeaveRequestId(),
                d.getDelegatorId(), delegatorName,
                d.getDelegateId(), delegateName,
                d.getDelegationScope(), d.getStatus(),
                d.getDelegateNotes(), d.getRespondedAt(), d.getCreatedAt());
    }
}
