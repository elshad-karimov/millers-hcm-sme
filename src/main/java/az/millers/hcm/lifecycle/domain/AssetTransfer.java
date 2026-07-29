package az.millers.hcm.lifecycle.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * M457 — Asset transfer request between employees (simple HR-approve flow).
 */
@Entity
@Table(name = "asset_transfer", schema = "lifecycle")
public class AssetTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(name = "transfer_no", nullable = false, unique = true, length = 20)
    private String transferNo;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "from_employee_id", nullable = false)
    private UUID fromEmployeeId;

    @Column(name = "to_employee_id", nullable = false)
    private UUID toEmployeeId;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetTransferStatus status = AssetTransferStatus.PENDING;

    @Column(name = "requested_by", length = 80)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "approved_by", length = 80)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTransferNo() {
        return transferNo;
    }

    public void setTransferNo(String transferNo) {
        this.transferNo = transferNo;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public UUID getFromEmployeeId() {
        return fromEmployeeId;
    }

    public void setFromEmployeeId(UUID fromEmployeeId) {
        this.fromEmployeeId = fromEmployeeId;
    }

    public UUID getToEmployeeId() {
        return toEmployeeId;
    }

    public void setToEmployeeId(UUID toEmployeeId) {
        this.toEmployeeId = toEmployeeId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public AssetTransferStatus getStatus() {
        return status;
    }

    public void setStatus(AssetTransferStatus status) {
        this.status = status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
