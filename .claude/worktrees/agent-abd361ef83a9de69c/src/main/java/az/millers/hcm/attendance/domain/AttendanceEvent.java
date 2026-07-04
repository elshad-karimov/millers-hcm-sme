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

@Entity
@Table(name = "attendance_event", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceEvent {

    @Id
    private UUID id;

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

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (importedAt == null) importedAt = OffsetDateTime.now();
    }
}
