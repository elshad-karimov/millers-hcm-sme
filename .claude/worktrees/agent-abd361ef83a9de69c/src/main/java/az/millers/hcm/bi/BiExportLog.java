package az.millers.hcm.bi;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistent audit record for every BI export request (PRD §17.6 / M55).
 *
 * <p>Maps to {@code reporting.bi_export_log}. Written by
 * {@link BiExportService#logExport} after each successful OData or CSV export.
 */
@Entity
@Table(schema = "reporting", name = "bi_export_log")
@Getter
@Setter
public class BiExportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** One of: employees, payroll_runs, leave_balances, attendance_summary, headcount_trend */
    @Column(name = "entity", nullable = false, length = 50)
    private String entity;

    /** ODATA or CSV */
    @Column(name = "format", nullable = false, length = 10)
    private String format;

    /** Keycloak {@code preferred_username} of the caller */
    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    /** Number of data rows included in the export */
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "exported_at", nullable = false)
    private OffsetDateTime exportedAt = OffsetDateTime.now();
}
