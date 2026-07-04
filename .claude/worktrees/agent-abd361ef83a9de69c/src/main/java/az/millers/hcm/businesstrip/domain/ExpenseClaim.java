package az.millers.hcm.businesstrip.domain;

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

/**
 * Post-trip expense claim (M104). One claim per trip (at most one active
 * at a time, enforced by a partial unique index in V71).
 */
@Entity
@Table(name = "expense_claim", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseClaim {

    @Id
    private UUID id;

    @Column(name = "claim_no", nullable = false, unique = true, updatable = false)
    private String claimNo;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(length = 10, nullable = false)
    private String currency = "AZN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by", length = 80)
    private String approvedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by", length = 80)
    private String createdBy;

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
