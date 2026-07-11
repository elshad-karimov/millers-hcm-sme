package az.millers.hcm.selfservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.domain.AttendanceEvent;
import az.millers.hcm.attendance.repo.AttendanceEventRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.organization.domain.Location;
import az.millers.hcm.organization.repo.LocationRepository;
import az.millers.hcm.selfservice.api.dto.GeofenceConfigResponse;
import az.millers.hcm.selfservice.api.dto.GeofenceLocationResponse;
import az.millers.hcm.selfservice.api.dto.MobilePunchRequest;
import az.millers.hcm.selfservice.api.dto.MobilePunchResponse;

/**
 * M495 — Mobile attendance punch service.
 * M497 — Geofence configuration.
 */
@Service
public class MobileAttendanceService {

    private static final String AUDIT_MODULE = "attendance";
    private static final String AUDIT_ENTITY = "mobile_punch";

    private final AttendanceEventRepository events;
    private final EmployeeContextService employeeContext;
    private final LocationRepository locations;
    private final SettingService settings;
    private final AuditService audit;

    public MobileAttendanceService(AttendanceEventRepository events,
                                   EmployeeContextService employeeContext,
                                   LocationRepository locations,
                                   SettingService settings,
                                   AuditService audit) {
        this.events = events;
        this.employeeContext = employeeContext;
        this.locations = locations;
        this.settings = settings;
        this.audit = audit;
    }

    /**
     * M495 — Records a mobile attendance punch. IDOR-safe: only the current
     * employee can punch. Idempotent on offlineQueueId. Validates geofence
     * if GPS coords provided + configured.
     */
    @Transactional
    public MobilePunchResponse punch(MobilePunchRequest req) {
        Employee emp = employeeContext.currentEmployee();
        UUID employeeId = emp.getId();

        // ── Idempotency check ──
        if (req.offlineQueueId() != null && !req.offlineQueueId().isBlank()) {
            if (events.existsByOfflineQueueId(req.offlineQueueId())) {
                // Already processed this offline punch — return existing event
                AttendanceEvent existing = events.findByOfflineQueueId(req.offlineQueueId())
                        .orElseThrow(() -> new BadRequestException("Offline queue ID collision"));
                return new MobilePunchResponse(
                        existing.getId(),
                        existing.getEventType(),
                        existing.getImportedAt(),
                        MobilePunchResponse.GeofenceStatus.UNKNOWN,
                        false
                );
            }
        }

        // ── Geofence validation ──
        MobilePunchResponse.GeofenceStatus geofenceStatus = MobilePunchResponse.GeofenceStatus.UNKNOWN;
        boolean flagged = false;

        if (req.latitude() != null && req.longitude() != null) {
            GeofenceConfigResponse config = getGeofenceConfig();
            if (config.geofencingConfigured()) {
                boolean insideAny = false;
                for (GeofenceLocationResponse loc : config.locations()) {
                    double distance = haversineDistanceM(
                            req.latitude(), req.longitude(),
                            loc.latitude().doubleValue(), loc.longitude().doubleValue()
                    );
                    if (distance <= loc.radiusM()) {
                        insideAny = true;
                        break;
                    }
                }
                geofenceStatus = insideAny
                        ? MobilePunchResponse.GeofenceStatus.INSIDE
                        : MobilePunchResponse.GeofenceStatus.OUTSIDE;
                if (!insideAny) {
                    flagged = true;
                }
            }
        }

        // ── Create attendance event ──
        AttendanceEvent event = new AttendanceEvent();
        event.setEmployeeId(employeeId);
        event.setEventTime(req.timestamp());
        event.setEventType(req.type());
        event.setDeviceId(req.deviceId());
        event.setOfflineQueueId(req.offlineQueueId());
        event.setSource(req.offlineQueueId() != null && !req.offlineQueueId().isBlank()
                ? "MOBILE_OFFLINE" : "MOBILE");

        // Store GPS coords in location field as JSON-like string for audit trail
        if (req.latitude() != null && req.longitude() != null) {
            event.setLocation(String.format("%.6f,%.6f (acc:%.1fm)",
                    req.latitude(), req.longitude(),
                    req.gpsAccuracy() != null ? req.gpsAccuracy() : 0.0));
        }

        try {
            events.save(event);
        } catch (DataIntegrityViolationException ex) {
            // Partial unique index collision (offline_queue_id)
            throw new BadRequestException("Duplicate offline punch detected");
        }

        // ── Audit ──
        audit.record(AUDIT_MODULE, AUDIT_ENTITY, event.getId().toString(),
                "MOBILE_PUNCH",
                null,
                String.format("type=%s, geofence=%s, flagged=%s, device=%s",
                        req.type(), geofenceStatus, flagged, req.deviceId()));

        return new MobilePunchResponse(
                event.getId(),
                event.getEventType(),
                event.getImportedAt(),
                geofenceStatus,
                flagged
        );
    }

    /**
     * M497 — Returns geofence locations for the current employee's work location
     * plus org-unit location. If no GPS coords exist, returns empty list + flag.
     */
    @Transactional(readOnly = true)
    public GeofenceConfigResponse getGeofenceConfig() {
        Employee emp = employeeContext.currentEmployee();
        List<GeofenceLocationResponse> geoLocations = new ArrayList<>();

        int radiusM = Integer.parseInt(settings.get("mobile.geofence_radius_m", "100"));

        // ── Employee's work location ──
        if (emp.getWorkLocationId() != null) {
            locations.findById(emp.getWorkLocationId()).ifPresent(loc -> {
                if (loc.getLatitude() != null && loc.getLongitude() != null) {
                    geoLocations.add(new GeofenceLocationResponse(
                            loc.getId(),
                            loc.getName(),
                            loc.getLatitude(),
                            loc.getLongitude(),
                            radiusM
                    ));
                }
            });
        }

        // ── Org unit location (if different) ──
        // Note: Employee entity doesn't have org_unit_id directly; would need to join
        // via department or position. For now, work_location is the primary geofence.
        // If needed, extend this to check employee.department.org_unit.location_id

        boolean configured = !geoLocations.isEmpty();
        return new GeofenceConfigResponse(geoLocations, configured);
    }

    /**
     * Haversine distance in meters.
     */
    private double haversineDistanceM(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
