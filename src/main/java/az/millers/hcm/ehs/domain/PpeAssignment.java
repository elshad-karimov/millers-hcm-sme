package az.millers.hcm.ehs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "ppe_assignment", schema = "ehs")
@Data
public class PpeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "ppe_item_id", nullable = false)
    private UUID ppeItemId;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "returned_at")
    private LocalDate returnedAt;

    @Column(name = "condition_at_issue", length = 200)
    private String conditionAtIssue;

    @Column(name = "condition_at_return", length = 200)
    private String conditionAtReturn;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
