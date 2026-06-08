package az.millers.hcm.organization.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.api.dto.LocationDtos.LocationRequest;
import az.millers.hcm.organization.api.dto.LocationDtos.LocationResponse;
import az.millers.hcm.organization.domain.Location;
import az.millers.hcm.organization.repo.LocationRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M141 — CRUD for the Location master (§11).
 *
 * <p>Invariants:
 * <ul>
 *   <li>Code is unique and immutable after creation.</li>
 *   <li>GPS fields are all-or-nothing: both latitude and longitude must
 *       be present together (enforced by DB CHECK; service mirrors it).</li>
 *   <li>Deactivate instead of hard-delete — historical employee/org-unit
 *       references stay resolvable.</li>
 * </ul>
 */
@Service
public class LocationService {

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY = "Location";

    private final LocationRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LocationService(LocationRepository repo,
                           AuditService audit,
                           CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<Location> list(boolean activeOnly) {
        return activeOnly ? repo.findByActiveTrueOrderByNameAsc()
                         : repo.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Location get(UUID id) {
        return repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Location not found: " + id));
    }

    @Transactional
    public Location create(LocationRequest req) {
        String code = req.code().trim();
        if (repo.existsByCode(code)) {
            throw new BadRequestException("Location code already exists: " + code);
        }
        validateGps(req);
        Location loc = new Location();
        loc.setCode(code);
        loc.setCreatedBy(currentRequest.username());
        loc.setUpdatedBy(currentRequest.username());
        apply(loc, req);
        Location saved = repo.save(loc);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, LocationResponse.from(saved));
        return saved;
    }

    @Transactional
    public Location update(UUID id, LocationRequest req) {
        Location loc = get(id);
        if (!loc.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("Location code is immutable");
        }
        validateGps(req);
        LocationResponse before = LocationResponse.from(loc);
        loc.setUpdatedBy(currentRequest.username());
        apply(loc, req);
        Location saved = repo.save(loc);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, LocationResponse.from(saved));
        return saved;
    }

    @Transactional
    public Location setActive(UUID id, boolean active) {
        Location loc = get(id);
        if (loc.isActive() == active) return loc;
        LocationResponse before = LocationResponse.from(loc);
        loc.setActive(active);
        loc.setUpdatedBy(currentRequest.username());
        Location saved = repo.save(loc);
        audit.record(MODULE, ENTITY, id.toString(),
                active ? "REACTIVATE" : "DEACTIVATE",
                before, LocationResponse.from(saved));
        return saved;
    }

    private void apply(Location loc, LocationRequest req) {
        loc.setName(req.name());
        loc.setLocationType(req.locationType());
        loc.setCountry(req.country());
        loc.setCity(req.city());
        loc.setRegion(req.region());
        loc.setAddress(req.address());
        loc.setLatitude(req.latitude());
        loc.setLongitude(req.longitude());
        loc.setTimezone(req.timezone());
        loc.setHolidayJurisdiction(req.holidayJurisdiction());
        loc.setWorkCalendarCode(req.workCalendarCode());
        loc.setDefaultShiftGroupId(req.defaultShiftGroupId());
        loc.setBranchManagerId(req.branchManagerId());
        loc.setLegalEntityId(req.legalEntityId());
        loc.setCostCentreCode(req.costCentreCode());
        loc.setPhone(req.phone());
        loc.setEmail(req.email());
        loc.setNotes(req.notes());
        if (req.active() != null) loc.setActive(req.active());
    }

    private void validateGps(LocationRequest req) {
        boolean hasLat = req.latitude() != null;
        boolean hasLon = req.longitude() != null;
        if (hasLat != hasLon) {
            throw new BadRequestException(
                    "latitude and longitude must both be provided or both be null");
        }
    }
}
