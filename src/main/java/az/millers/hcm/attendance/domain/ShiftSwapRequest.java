package az.millers.hcm.attendance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * M482: Shift swap request between two employees.
 */
@Entity
@Table(name = "shift_swap_request", schema = "attendance")
public class ShiftSwapRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(name = "request_no", nullable = false, unique = true, length = 20)
    private String requestNo;

    @Column(name = "roster_entry_id", nullable = false)
    private UUID rosterEntryId;

    @Column(name = "from_employee_id", nullable = false)
    private UUID fromEmployeeId;

    @Column(name = "to_employee_id", nullable = false)
    private UUID toEmployeeId;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 80)
    private String approvedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }

    public UUID getRosterEntryId() { return rosterEntryId; }
    public void setRosterEntryId(UUID rosterEntryId) { this.rosterEntryId = rosterEntryId; }

    public UUID getFromEmployeeId() { return fromEmployeeId; }
    public void setFromEmployeeId(UUID fromEmployeeId) { this.fromEmployeeId = fromEmployeeId; }

    public UUID getToEmployeeId() { return toEmployeeId; }
    public void setToEmployeeId(UUID toEmployeeId) { this.toEmployeeId = toEmployeeId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
