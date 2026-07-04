package az.millers.hcm.attendance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.attendance.api.dto.DeviceDtos.DeviceRequest;
import az.millers.hcm.attendance.api.dto.DeviceDtos.DeviceResponse;
import az.millers.hcm.attendance.domain.DeviceMaster;
import az.millers.hcm.attendance.repo.DeviceMasterRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.organization.repo.LegalEntityRepository;

/**
 * M333: Device master service.
 *
 * <p>Manages attendance devices (turnstiles, biometric, mobile apps).
 */
@Service
@Transactional
public class DeviceMasterService {

    private static final String MODULE = "attendance";
    private static final String ENTITY_TYPE = "device_master";

    private final DeviceMasterRepository repository;
    private final AuditService auditService;
    private final LegalEntityRepository legalEntities;

    public DeviceMasterService(DeviceMasterRepository repository, AuditService auditService,
                                LegalEntityRepository legalEntities) {
        this.repository = repository;
        this.auditService = auditService;
        this.legalEntities = legalEntities;
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        UUID tenantId = defaultTenantId();
        return repository.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .map(DeviceResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID id) {
        UUID tenantId = defaultTenantId();
        DeviceMaster device = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Device not found: " + id));
        return DeviceResponse.from(device);
    }

    public DeviceResponse create(DeviceRequest req, String createdBy) {
        UUID tenantId = defaultTenantId();
        repository.findByTenantIdAndCode(tenantId, req.code()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Device code already exists: " + req.code());
        });

        DeviceMaster device = new DeviceMaster();
        device.setTenantId(tenantId);
        device.setCode(req.code());
        device.setName(req.name());
        device.setDeviceType(req.deviceType() != null ? req.deviceType() : "TURNSTILE");
        device.setLocationId(req.locationId());
        device.setIpAddress(req.ipAddress());
        device.setSerialNumber(req.serialNumber());
        device.setActive(true);

        device = repository.save(device);

        auditService.record(MODULE, ENTITY_TYPE, device.getId().toString(),
                "DEVICE_CREATED", null,
                Map.of("code", req.code(), "type", device.getDeviceType()));

        return DeviceResponse.from(device);
    }

    public DeviceResponse update(UUID id, DeviceRequest req, String updatedBy) {
        UUID tenantId = defaultTenantId();
        DeviceMaster device = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Device not found: " + id));

        if (!device.getCode().equals(req.code())) {
            repository.findByTenantIdAndCode(tenantId, req.code()).ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Device code already exists: " + req.code());
            });
            device.setCode(req.code());
        }

        device.setName(req.name());
        device.setDeviceType(req.deviceType() != null ? req.deviceType() : device.getDeviceType());
        device.setLocationId(req.locationId());
        device.setIpAddress(req.ipAddress());
        device.setSerialNumber(req.serialNumber());

        device = repository.save(device);

        auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                "DEVICE_UPDATED", null,
                Map.of("code", device.getCode()));

        return DeviceResponse.from(device);
    }

    public void deactivate(UUID id) {
        UUID tenantId = defaultTenantId();
        DeviceMaster device = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Device not found: " + id));

        device.setActive(false);
        repository.save(device);

        auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                "DEVICE_DEACTIVATED", null,
                Map.of("code", device.getCode()));
    }

    public void activate(UUID id) {
        UUID tenantId = defaultTenantId();
        DeviceMaster device = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Device not found: " + id));

        device.setActive(true);
        repository.save(device);

        auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                "DEVICE_ACTIVATED", null,
                Map.of("code", device.getCode()));
    }

    public void recordActivity(String deviceCode) {
        UUID tenantId = defaultTenantId();
        repository.findByTenantIdAndCode(tenantId, deviceCode).ifPresent(device -> {
            device.setLastSeenAt(OffsetDateTime.now());
            repository.save(device);
        });
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found")).getId();
    }
}
