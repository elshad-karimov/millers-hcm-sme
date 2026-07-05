package az.millers.hcm.businesstrip.domain;

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

/**
 * Employee mileage claim for business use of personal/company vehicle (M453).
 * DRAFT → SUBMITTED (employee) → APPROVED/REJECTED (manager or HR) → PAID (HR).
 */
@Entity
@Table(name = "mileage_claim", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class MileageClaim {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";

    @Column(name = "claim_no", nullable = false, unique = true, updatable = false, length = 20)
    private String claimNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "start_location", nullable = false, length = 200)
    private String startLocation;

    @Column(name = "end_location", nullable = false, length = 200)
    private String endLocation;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 1)
    private BigDecimal distanceKm;

    @Column(name = "rate_per_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal ratePerKm = new BigDecimal("0.30");

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MileageClaimStatus status = MileageClaimStatus.DRAFT;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
        if (ratePerKm == null) ratePerKm = new BigDecimal("0.30");
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
