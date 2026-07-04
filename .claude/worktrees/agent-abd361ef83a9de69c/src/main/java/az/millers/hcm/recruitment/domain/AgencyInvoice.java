package az.millers.hcm.recruitment.domain;

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

/** M296 — a placement invoice raised against an agency submission (PRD Phase F). */
@Entity
@Table(name = "agency_invoice", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class AgencyInvoice {

    @Id
    private UUID id;

    @Column(name = "invoice_no", nullable = false, unique = true, length = 20)
    private String invoiceNo;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "hire_employee_id")
    private UUID hireEmployeeId;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (issuedAt == null) issuedAt = now;
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
