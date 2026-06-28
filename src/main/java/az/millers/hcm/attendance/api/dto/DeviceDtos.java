package az.millers.hcm.attendance.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.DeviceMaster;

public class DeviceDtos {

    public record DeviceRequest(
            String code,
            String name,
            String deviceType,
            UUID locationId,
            String ipAddress,
            String serialNumber) {
    }

    public record DeviceResponse(
            UUID id,
            UUID tenantId,
            String code,
            String name,
            String deviceType,
            UUID locationId,
            String ipAddress,
            String serialNumber,
            boolean active,
            OffsetDateTime lastSeenAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static DeviceResponse from(DeviceMaster entity) {
            return new DeviceResponse(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getCode(),
                    entity.getName(),
                    entity.getDeviceType(),
                    entity.getLocationId(),
                    entity.getIpAddress(),
                    entity.getSerialNumber(),
                    entity.isActive(),
                    entity.getLastSeenAt(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt());
        }
    }
}
