package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.GrantStatus;
import az.millers.hcm.staffing.domain.PositionProfileGrant;
import az.millers.hcm.staffing.domain.ProfileItemType;
import jakarta.validation.constraints.Size;

public final class PositionProfileGrantDtos {

    private PositionProfileGrantDtos() {}

    public record GrantResponse(
            UUID id, UUID occupancyId, UUID profileItemId,
            UUID employeeId, UUID positionId,
            ProfileItemType itemType, String label,
            BigDecimal valueAmount, String currency,
            String referenceCode, String notes,
            GrantStatus status,
            OffsetDateTime grantedAt, String grantedBy,
            OffsetDateTime revokedAt, String revokedBy, String revokeReason,
            String failureReason,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {

        public static GrantResponse from(PositionProfileGrant g) {
            return new GrantResponse(g.getId(), g.getOccupancyId(), g.getProfileItemId(),
                    g.getEmployeeId(), g.getPositionId(),
                    g.getItemType(), g.getLabel(),
                    g.getValueAmount(), g.getCurrency(),
                    g.getReferenceCode(), g.getNotes(),
                    g.getStatus(),
                    g.getGrantedAt(), g.getGrantedBy(),
                    g.getRevokedAt(), g.getRevokedBy(), g.getRevokeReason(),
                    g.getFailureReason(),
                    g.getCreatedAt(), g.getUpdatedAt());
        }
    }

    public record ReasonRequest(@Size(max = 2000) String reason) {}
}
