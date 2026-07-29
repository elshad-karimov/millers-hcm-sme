package az.millers.hcm.engagement.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.TenantId;

/**
 * M478 — Peer recognition/kudos (distinct from HR-granted EmployeeReward).
 */
@Entity
@Table(name = "recognition", schema = "engagement")
public class EmployeeRecognition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "from_employee_id", nullable = false)
    private UUID fromEmployeeId;

    @Column(name = "to_employee_id", nullable = false)
    private UUID toEmployeeId;

    @Column(name = "value_tag", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RecognitionValueTag valueTag;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RecognitionVisibility visibility = RecognitionVisibility.PUBLIC;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RecognitionStatus status = RecognitionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public UUID getFromEmployeeId() { return fromEmployeeId; }
    public void setFromEmployeeId(UUID fromEmployeeId) { this.fromEmployeeId = fromEmployeeId; }

    public UUID getToEmployeeId() { return toEmployeeId; }
    public void setToEmployeeId(UUID toEmployeeId) { this.toEmployeeId = toEmployeeId; }

    public RecognitionValueTag getValueTag() { return valueTag; }
    public void setValueTag(RecognitionValueTag valueTag) { this.valueTag = valueTag; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public RecognitionVisibility getVisibility() { return visibility; }
    public void setVisibility(RecognitionVisibility visibility) { this.visibility = visibility; }

    public RecognitionStatus getStatus() { return status; }
    public void setStatus(RecognitionStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
