package az.millers.hcm.ehs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "corrective_action", schema = "ehs")
@Data
public class CorrectiveAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "inspection_id")
    private UUID inspectionId;

    @Column(name = "risk_assessment_id")
    private UUID riskAssessmentId;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "responsible_username", nullable = false, length = 255)
    private String responsibleUsername;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private CorrectiveActionPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CorrectiveActionStatus status = CorrectiveActionStatus.OPEN;

    @Column(name = "evidence_attachment_id")
    private UUID evidenceAttachmentId;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

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
