package az.millers.hcm.attendance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M333: Attendance device master.
 *
 * <p>Registers turnstiles, biometric devices, mobile apps, etc.
 * Tracks last activity for monitoring.
 */
@Entity
@Table(name = "device_master", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class DeviceMaster {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(name = "device_type", length = 30, nullable = false)
    private String deviceType = "TURNSTILE";

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
