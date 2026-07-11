package az.millers.hcm.selfservice.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * M497 — Geofence location data for mobile attendance.
 */
public record GeofenceLocationResponse(
        UUID locationId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusM
) {
}
