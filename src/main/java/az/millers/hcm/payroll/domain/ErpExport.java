package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A payroll ERP export batch (M158 / §17.2).
 *
 * <p>Generated from an APPROVED or CLOSED payroll run.  Contains double-entry
 * journal lines (debit/credit) for upload into 1C, Dynamics 365, or a generic
 * accounting system via CSV or JSON.
 */
@Entity
@Table(name = "erp_export", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class ErpExport {

    @Id
    private UUID id;

    @Column(name = "export_no", nullable = false, unique = true)
    private String exportNo;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    /** CSV_GENERIC | CSV_1C | CSV_DYNAMICS365 | JSON */
    @Column(nullable = false)
    private String format;

    /** PENDING | GENERATING | READY | FAILED */
    @Column(nullable = false)
    private String status;

    @Column(name = "posting_date")
    private LocalDate postingDate;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "journal_type")
    private String journalType;

    @Column(name = "line_count")
    private int lineCount;

    @Column(name = "total_debit", precision = 18, scale = 2)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", precision = 18, scale = 2)
    private BigDecimal totalCredit;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @OneToMany(mappedBy = "export", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<ErpExportLine> lines = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = "PENDING";
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
