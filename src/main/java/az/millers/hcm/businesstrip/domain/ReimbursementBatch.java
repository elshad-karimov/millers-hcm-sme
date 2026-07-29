package az.millers.hcm.businesstrip.domain;

import java.math.BigDecimal;
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
 * Expense reimbursement payment batch (M455).
 * Groups approved expense claims for payment processing.
 */
@Entity
@Table(name = "reimbursement_batch", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class ReimbursementBatch {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(name = "batch_no", nullable = false, unique = true, updatable = false, length = 20)
    private String batchNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReimbursementBatchStatus status = ReimbursementBatchStatus.DRAFT;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "payment_ref", length = 100)
    private String paymentRef;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (currency == null) currency = "AZN";
    }
}
