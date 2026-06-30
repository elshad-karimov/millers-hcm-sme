package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "annual_tax_certificate", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class AnnualTaxCertificate {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "annual_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualGross = BigDecimal.ZERO;

    @Column(name = "exempt_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal exemptAmount = BigDecimal.ZERO;

    @Column(name = "taxable_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableGross = BigDecimal.ZERO;

    @Column(name = "total_tax_withheld", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalTaxWithheld = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertificateStatus status;

    @Column(name = "pdf_storage_path", length = 500)
    private String pdfStoragePath;

    @Column(name = "pdf_attachment_id")
    private UUID pdfAttachmentId;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "generated_by", length = 160)
    private String generatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
