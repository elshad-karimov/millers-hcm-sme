package az.millers.hcm.lifecycle.domain;

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

@Entity
@Table(name = "offboarding_asset_return", schema = "lifecycle")
@Getter @Setter @NoArgsConstructor
public class OffboardingAssetReturn {

    @Id private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "employee_asset_id")
    private UUID employeeAssetId;

    @Column(name = "asset_type", nullable = false, length = 40)
    private String assetType;

    @Column(name = "asset_name", nullable = false, length = 200)
    private String assetName;

    @Column(name = "asset_identifier", length = 120)
    private String assetIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_status", nullable = false, length = 40)
    private AssetReturnStatus returnStatus = AssetReturnStatus.PENDING_RETURN;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "condition_at_return", length = 40)
    private String conditionAtReturn;

    @Column(name = "deduction_amount", precision = 12, scale = 2)
    private BigDecimal deductionAmount;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now; updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
