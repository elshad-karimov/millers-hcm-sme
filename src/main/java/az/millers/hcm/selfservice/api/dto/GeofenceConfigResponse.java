package az.millers.hcm.selfservice.api.dto;

import java.util.List;

/**
 * M497 — Geofence configuration response.
 */
public record GeofenceConfigResponse(
        List<GeofenceLocationResponse> locations,
        boolean geofencingConfigured
) {
}
