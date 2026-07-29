package az.millers.hcm.corehr.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/**
 * One bulk-import attempt (M69 / P1-16). Tracks both dry-run previews and
 * committed runs so HR can audit "what was imported by whom" without trawling
 * the per-employee audit log.
 *
 * <p>The error report is stored as JSONB — a list of
 * {@code {row, message, fields[]}} entries. Schema simplicity wins over a
 * dedicated per-error child table since the use case is occasional and the
 * report is consumed wholesale.
 */
@Entity
@Table(name = "employee_import_job", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeImportJob {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "started_by", nullable = false, length = 80)
    private String startedBy;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportJobStatus status = ImportJobStatus.PENDING;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_valid", nullable = false)
    private int rowsValid;

    @Column(name = "rows_invalid", nullable = false)
    private int rowsInvalid;

    @Column(name = "rows_committed", nullable = false)
    private int rowsCommitted;

    /** JSONB — list of {@code {row, message, fields[]}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_report_json", columnDefinition = "jsonb")
    private Object errorReport;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }
}
