package az.millers.hcm.attendance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persists non-imported (DUPLICATE or FAILED) rows from a CSV batch so that
 * HR admins can review them and trigger a retry without re-uploading the file.
 */
@Entity
@Table(name = "turnstile_import_row", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class TurnstileImportRow {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "raw_line")
    private String rawLine;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "event_time")
    private OffsetDateTime eventTime;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "device_id")
    private String deviceId;

    /** DUPLICATE or FAILED */
    @Column(name = "row_status", nullable = false)
    private String rowStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
