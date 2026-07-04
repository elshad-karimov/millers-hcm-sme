package az.millers.hcm.leave.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.leave.domain.LeaveGroup;

public record LeaveGroupResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean defaultGroup,
        boolean active,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static LeaveGroupResponse from(LeaveGroup g) {
        return new LeaveGroupResponse(
                g.getId(),
                g.getCode(),
                g.getName(),
                g.getDescription(),
                g.isDefaultGroup(),
                g.isActive(),
                g.getCreatedAt(),
                g.getCreatedBy(),
                g.getUpdatedAt(),
                g.getUpdatedBy());
    }
}
