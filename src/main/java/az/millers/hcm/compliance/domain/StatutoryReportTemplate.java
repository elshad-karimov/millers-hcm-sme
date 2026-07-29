package az.millers.hcm.compliance.domain;

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

/**
 * M468 — Statutory report template definition (monthly/quarterly/annual filings).
 */
@Entity
@Table(name = "statutory_report_template", schema = "compliance")
@Getter
@Setter
@NoArgsConstructor
public class StatutoryReportTemplate {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 2)
    private String country = "AZ";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 10)
    private FileFormat fileFormat;

    @Column(name = "due_day", nullable = false)
    private int dueDay = 20;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    public enum ReportFrequency {
        MONTHLY,
        QUARTERLY,
        ANNUAL
    }

    public enum FileFormat {
        XLSX,
        CSV
    }
}
