package az.millers.hcm.corehr.domain;

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
 * Company asset assigned to an employee (M72 / P2-09). Plays into offboarding
 * — the {@code lifecycle.termination_request.clearance_assets} flag is the
 * overall confirmation, but this row carries the specific items that must be
 * returned.
 */
@Entity
@Table(name = "employee_asset", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeAsset {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    /** Serial number / asset tag — the unique-ish identifier on the device itself. */
    @Column(name = "asset_identifier", length = 120)
    private String assetIdentifier;

    @Column(name = "asset_name", nullable = false, length = 160)
    private String assetName;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.ASSIGNED;

    @Column(name = "assigned_at", nullable = false)
    private LocalDate assignedAt;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "returned_at")
    private LocalDate returnedAt;

    @Column(name = "condition_at_assignment", length = 40)
    private String conditionAtAssignment;

    @Column(name = "condition_at_return", length = 40)
    private String conditionAtReturn;

    @Column(name = "return_accepted_by", length = 80)
    private String returnAcceptedBy;

    /** Optional pointer to a signed custody form stored in MinIO via AttachmentService. */
    @Column(name = "custody_form_url", length = 500)
    private String custodyFormUrl;

    @Column(columnDefinition = "text")
    private String notes;

    // ── M128 — depreciation fields ──────────────────────────────────────────

    /** Acquisition cost. Null → asset doesn't carry a financial value. */
    @Column(name = "purchase_cost", precision = 14, scale = 2)
    private java.math.BigDecimal purchaseCost;

    /** Acquisition date. Drives the period 0 of the schedule. */
    @Column(name = "purchase_date")
    private java.time.LocalDate purchaseDate;

    /** Useful life in months. Must be positive when method ≠ NONE. */
    @Column(name = "useful_life_months")
    private Integer usefulLifeMonths;

    /** Residual value at end-of-life. Book value floors here. */
    @Column(name = "salvage_value", precision = 14, scale = 2)
    private java.math.BigDecimal salvageValue;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 32)
    private DepreciationMethod depreciationMethod = DepreciationMethod.NONE;

    /** Annual % used by DECLINING_BALANCE. Null = double-declining default. */
    @Column(name = "declining_rate_percent", precision = 5, scale = 2)
    private java.math.BigDecimal decliningRatePercent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

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
