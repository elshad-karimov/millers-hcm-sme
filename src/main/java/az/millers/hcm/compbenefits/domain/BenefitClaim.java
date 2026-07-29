package az.millers.hcm.compbenefits.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.hibernate.annotations.TenantId;

/** HCM_11 M381 — a benefit reimbursement claim. */
@Entity
@Table(name = "benefit_claim", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitClaim {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "claim_no", nullable = false, length = 40)
    private String claimNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    @Column(nullable = false, length = 10)
    private String currency = "AZN";

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "approved_amount", precision = 14, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenefitClaimStatus status = BenefitClaimStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "submitted_by", length = 80)
    private String submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @Column(name = "paid_by", length = 80)
    private String paidBy;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

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
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
