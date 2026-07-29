package az.millers.hcm.attendance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "attendance_event", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceEvent {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "device_employee_code")
    private String deviceEmployeeCode;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "device_id")
    private String deviceId;

    private String location;

    @Column(nullable = false)
    private String source;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private OffsetDateTime importedAt;

    /** M495 — Mobile offline queue deduplication key. */
    @Column(name = "offline_queue_id", length = 120)
    private String offlineQueueId;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (importedAt == null) importedAt = OffsetDateTime.now();
    }
}
