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

@Entity
@Table(name = "turnstile_import_batch", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class TurnstileImportBatch {

    @Id
    private UUID id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "imported_by", nullable = false)
    private String importedBy;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private OffsetDateTime importedAt;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(nullable = false)
    private String status;  // COMPLETED | PARTIAL | FAILED

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (importedAt == null) importedAt = OffsetDateTime.now();
    }
}
