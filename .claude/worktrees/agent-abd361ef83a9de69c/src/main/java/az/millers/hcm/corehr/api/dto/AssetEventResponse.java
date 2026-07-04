package az.millers.hcm.corehr.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.AssetEvent;
import az.millers.hcm.corehr.domain.AssetEventType;
import az.millers.hcm.corehr.domain.AssetStatus;

/**
 * M124 — wire shape of one row from
 * {@link AssetEvent} on the asset event-log timeline.
 */
public record AssetEventResponse(
        UUID id,
        UUID assetId,
        AssetEventType eventType,
        OffsetDateTime occurredAt,
        String actor,
        AssetStatus previousStatus,
        AssetStatus newStatus,
        UUID previousEmployeeId,
        UUID newEmployeeId,
        String condition,
        String notes) {

    public static AssetEventResponse from(AssetEvent e) {
        return new AssetEventResponse(
                e.getId(), e.getAssetId(), e.getEventType(), e.getOccurredAt(),
                e.getActor(),
                e.getPreviousStatus(), e.getNewStatus(),
                e.getPreviousEmployeeId(), e.getNewEmployeeId(),
                e.getCondition(), e.getNotes());
    }
}
